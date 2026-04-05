package kz.ktj.digitaltwin.healthindex.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class HealthIndexResult {

    private String locomotiveId;
    private String locomotiveType;
    private Instant calculatedAt;
    private double score;
    private HealthCategory category;
    private HealthTrend trend;
    private List<FactorContribution> topFactors;
    private double dtcPenalty;
    private int activeAlerts;

    public enum HealthCategory {
        NORMAL,    // 75–100
        ATTENTION, // 50–74
        CRITICAL   // 0–49
    }

    public enum HealthTrend {
        IMPROVING,
        STABLE,
        DEGRADING
    }

    @Data
    @Builder
    public static class FactorContribution {
        private String paramName;
        private String displayName;
        private double rawValue;
        private double normalizedDeviation;
        private double weight;
        private double impact;
        private String severity;
    }
}
