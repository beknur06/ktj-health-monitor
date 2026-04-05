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

/**
 * Health index formula (DB-driven):
 *
 *   score = 100 − Σ(weight × √deviation × 100 × multiplier) − dtcPenalty
 *
 * √deviation replaces the old deviation^0.8 — it is more sensitive at small
 * deviations, making the score visibly react as parameters drift toward limits.
 *
 * All parameters (which params, their weights, thresholds, and penalty multipliers)
 * are loaded from the health_param_weights table and cached for 60 seconds,
 * so they can be tuned at runtime via the /api/v1/health/config endpoint without
 * redeploying the service.
 *
 * multiplier tiers (per row in health_param_weights):
 *   deviation < warningThreshold  → 1.0               (normal operating range edge)
 *   warningThreshold ≤ dev < criticalThreshold → 1.5  (WARNING)
 *   deviation ≥ criticalThreshold → penaltyMultiplier  (CRITICAL — DB-configurable)
 */
@Service
public class HealthIndexCalculator {

    private static final Logger log = LoggerFactory.getLogger(HealthIndexCalculator.class);

    private static final double DTC_PENALTY_PER_CODE = 8.0;
    private static final double MAX_DTC_PENALTY      = 20.0;
    private static final double WARNING_MULTIPLIER   = 1.5;
    private static final long   CACHE_TTL_MS         = 60_000;

    private final HealthParamWeightRepository weightRepository;
    private final DeviationCalculator deviationCalculator;
    private final TrendTracker trendTracker;

    private volatile List<HealthParamWeight> weightCache = new ArrayList<>();
    private volatile long lastCacheRefresh = 0;

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

        List<FactorContribution> factors = new ArrayList<>();
        double totalImpact = 0;

        for (HealthParamWeight cfg : weightCache) {
            if (!isApplicable(cfg.getApplicableTo(), locoType)) continue;

            Double rawValue = params.get(cfg.getParamName());
            if (rawValue == null) continue;

            double deviation = deviationCalculator.calculate(cfg.getParamName(), rawValue);
            if (deviation < 0) continue; // param not in DeviationCalculator ranges

            // √deviation: sensitive across full range, still reaches 100 at deviation=1.0
            double penalty = deviation > 0 ? Math.sqrt(deviation) * 100.0 : 0.0;

            String severity;
            double multiplier;
            if (deviation >= cfg.getCriticalThreshold()) {
                severity   = "CRITICAL";
                multiplier = cfg.getPenaltyMultiplier();
            } else if (deviation >= cfg.getWarningThreshold()) {
                severity   = "WARNING";
                multiplier = WARNING_MULTIPLIER;
            } else {
                severity   = "NORMAL";
                multiplier = 1.0;
            }

            double impact = cfg.getWeight() * penalty * multiplier;
            totalImpact += impact;

            factors.add(FactorContribution.builder()
                .paramName(cfg.getParamName())
                .displayName(cfg.getDisplayName())
                .rawValue(rawValue)
                .normalizedDeviation(round(deviation))
                .weight(cfg.getWeight())
                .impact(round(impact))
                .severity(severity)
                .build());
        }

        double dtcPenalty = 0;
        if (envelope.getActiveDtcCodes() != null && !envelope.getActiveDtcCodes().isEmpty()) {
            dtcPenalty = Math.min(MAX_DTC_PENALTY,
                envelope.getActiveDtcCodes().size() * DTC_PENALTY_PER_CODE);
        }

        double score = round(Math.max(0, Math.min(100, 100 - totalImpact - dtcPenalty)));

        HealthCategory category = score >= 80 ? HealthCategory.NORMAL
                                : score >= 55 ? HealthCategory.ATTENTION
                                :               HealthCategory.CRITICAL;

        List<FactorContribution> topFactors = factors.stream()
            .filter(f -> f.getImpact() > 0.1)
            .sorted(Comparator.comparingDouble(FactorContribution::getImpact).reversed())
            .limit(5)
            .toList();

        HealthIndexResult.HealthTrend trend =
            trendTracker.updateAndGetTrend(envelope.getLocomotiveId(), score);

        log.debug("[{}] health={} category={} trend={} factors={} dtc={}",
            envelope.getLocomotiveId(), score, category, trend,
            factors.size(), round(dtcPenalty));

        return HealthIndexResult.builder()
            .locomotiveId(envelope.getLocomotiveId())
            .locomotiveType(locoType)
            .calculatedAt(Instant.now())
            .score(score)
            .category(category)
            .trend(trend)
            .topFactors(topFactors)
            .dtcPenalty(round(dtcPenalty))
            .activeAlerts(envelope.getActiveDtcCodes() != null
                ? envelope.getActiveDtcCodes().size() : 0)
            .build();
    }

    private boolean isApplicable(String applicableTo, String locoType) {
        return "BOTH".equals(applicableTo)
            || "ANY".equals(applicableTo)
            || applicableTo.equals(locoType);
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh > CACHE_TTL_MS) {
            weightCache = weightRepository.findAll();
            lastCacheRefresh = now;
            log.debug("Weight cache refreshed: {} entries", weightCache.size());
        }
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
