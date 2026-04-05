package kz.ktj.digitaltwin.healthindex.service;

import kz.ktj.digitaltwin.healthindex.dto.HealthIndexResult.HealthTrend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrendTracker {

    private final int windowSize;
    private final ConcurrentHashMap<String, Deque<Double>> windows = new ConcurrentHashMap<>();

    public TrendTracker(@Value("${health-index.trend-window-size:30}") int windowSize) {
        this.windowSize = windowSize;
    }

    public HealthTrend updateAndGetTrend(String locomotiveId, double score) {
        Deque<Double> window = windows.computeIfAbsent(locomotiveId, k -> new ArrayDeque<>());

        window.addLast(score);
        if (window.size() > windowSize) {
            window.removeFirst();
        }

        if (window.size() < 5) {
            return HealthTrend.STABLE;
        }

        double slope = calculateSlope(window);

        if (slope > 0.5) return HealthTrend.IMPROVING;
        if (slope < -0.5) return HealthTrend.DEGRADING;
        return HealthTrend.STABLE;
    }

    private double calculateSlope(Deque<Double> values) {
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        int i = 0;
        for (Double y : values) {
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumX2 += (double) i * i;
            i++;
        }

        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) return 0;

        return (n * sumXY - sumX * sumY) / denom;
    }
}
