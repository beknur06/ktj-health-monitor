package kz.ktj.digitaltwin.healthindex.service;

import kz.ktj.digitaltwin.healthindex.dto.HealthIndexResult;
import kz.ktj.digitaltwin.healthindex.dto.HealthIndexResult.FactorContribution;
import kz.ktj.digitaltwin.healthindex.dto.HealthIndexResult.HealthCategory;
import kz.ktj.digitaltwin.healthindex.dto.TelemetryEnvelope;
import kz.ktj.digitaltwin.healthindex.entity.HealthParamWeight;
import kz.ktj.digitaltwin.healthindex.repository.HealthParamWeightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// score = 100 - Σ(weight × deviation^0.8 × 100 × multiplier) - dtcPenalty
@Service
public class HealthIndexCalculator {

    private static final Logger log = LoggerFactory.getLogger(HealthIndexCalculator.class);
    private static final double DTC_PENALTY_PER_CODE = 10.0;
    private static final double DEVIATION_EXPONENT   = 0.8;   // <1 = harsher for partial deviations
    private static final double WARNING_MULTIPLIER   = 1.5;

    private final HealthParamWeightRepository weightRepository;
    private final DeviationCalculator deviationCalculator;
    private final TrendTracker trendTracker;

    private volatile Map<String, HealthParamWeight> weightCache = new HashMap<>();
    private volatile long lastCacheRefresh = 0;
    private static final long CACHE_TTL_MS = 60_000;

    public HealthIndexCalculator(HealthParamWeightRepository weightRepository,
                                  DeviationCalculator deviationCalculator,
                                  TrendTracker trendTracker) {
        this.weightRepository = weightRepository;
        this.deviationCalculator = deviationCalculator;
        this.trendTracker = trendTracker;
    }

    public HealthIndexResult calculate(TelemetryEnvelope envelope) {
        refreshCacheIfNeeded();

        String locoType = envelope.getLocomotiveType();
        Map<String, Double> params = envelope.getParameters();

        List<FactorContribution> allFactors = new ArrayList<>();
        double totalImpact = 0;

        for (Map.Entry<String, HealthParamWeight> entry : weightCache.entrySet()) {
            String paramName = entry.getKey();
            HealthParamWeight config = entry.getValue();

            if (!isApplicable(config.getApplicableTo(), locoType)) continue;

            Double rawValue = params.get(paramName);
            if (rawValue == null) continue;

            double deviation = deviationCalculator.calculate(paramName, rawValue);
            if (deviation < 0) continue;

            double curvedDev = deviation > 0 ? Math.pow(deviation, DEVIATION_EXPONENT) : 0;

            String severity;
            double multiplier;
            if (deviation >= config.getCriticalThreshold()) {
                severity = "CRITICAL";
                multiplier = config.getPenaltyMultiplier();
            } else if (deviation >= config.getWarningThreshold()) {
                severity = "WARNING";
                multiplier = WARNING_MULTIPLIER;
            } else {
                severity = "NORMAL";
                multiplier = 1.0;
            }

            double impact = config.getWeight() * curvedDev * 100.0 * multiplier;
            totalImpact += impact;

            allFactors.add(FactorContribution.builder()
                .paramName(paramName)
                .displayName(config.getDisplayName())
                .rawValue(rawValue)
                .normalizedDeviation(round(curvedDev))
                .weight(config.getWeight())
                .impact(round(impact))
                .severity(severity)
                .build());
        }

        double dtcPenalty = envelope.getActiveDtcCodes() != null
            ? envelope.getActiveDtcCodes().size() * DTC_PENALTY_PER_CODE : 0;

        double score = round(Math.max(0, Math.min(100, 100 - totalImpact - dtcPenalty)));

        HealthCategory category;
        if (score >= 80) category = HealthCategory.NORMAL;
        else if (score >= 60) category = HealthCategory.ATTENTION;
        else category = HealthCategory.CRITICAL;

        List<FactorContribution> topFactors = allFactors.stream()
            .filter(f -> f.getImpact() > 0.1)
            .sorted(Comparator.comparingDouble(FactorContribution::getImpact).reversed())
            .limit(5)
            .toList();

        HealthIndexResult.HealthTrend trend = trendTracker.updateAndGetTrend(envelope.getLocomotiveId(), score);

        return HealthIndexResult.builder()
            .locomotiveId(envelope.getLocomotiveId())
            .locomotiveType(locoType)
            .calculatedAt(Instant.now())
            .score(score)
            .category(category)
            .trend(trend)
            .topFactors(topFactors)
            .dtcPenalty(round(dtcPenalty))
            .activeAlerts(envelope.getActiveDtcCodes() != null ? envelope.getActiveDtcCodes().size() : 0)
            .build();
    }

    private boolean isApplicable(String applicableTo, String locoType) {
        if ("BOTH".equals(applicableTo) || "ANY".equals(applicableTo)) return true;
        return applicableTo.equals(locoType);
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh > CACHE_TTL_MS) {
            List<HealthParamWeight> all = weightRepository.findAll();
            Map<String, HealthParamWeight> newCache = new LinkedHashMap<>();
            for (HealthParamWeight w : all) newCache.put(w.getParamName(), w);
            weightCache = newCache;
            lastCacheRefresh = now;
            log.debug("Refreshed weight cache: {} entries", newCache.size());
        }
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
