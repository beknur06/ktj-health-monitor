package kz.ktj.digitaltwin.healthindex.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DeviationCalculator {

    private record Range(double normalMin, double normalMax, double critLow, double critHigh) {}

    private static final Map<String, Range> RANGES = Map.ofEntries(
        Map.entry("coolant_temp",            new Range(60, 90, 40, 105)),
        Map.entry("oil_temp",                new Range(65, 85, 40, 100)),
        Map.entry("exhaust_temp",            new Range(350, 520, 150, 600)),
        Map.entry("traction_motor_temp",     new Range(60, 120, 30, 160)),
        Map.entry("transformer_oil_temp",    new Range(40, 75, 20, 95)),
        Map.entry("oil_pressure",            new Range(0.30, 0.60, 0.15, 0.80)),
        Map.entry("brake_pipe_pressure",     new Range(0.45, 0.52, 0.35, 0.60)),
        Map.entry("main_reservoir_pressure", new Range(0.75, 0.90, 0.55, 1.0)),
        Map.entry("boost_pressure",          new Range(900, 1800, 600, 2500)),
        Map.entry("fuel_level",              new Range(20, 100, 10, 100)),
        Map.entry("fuel_rate",               new Range(30, 300, 0, 400)),
        Map.entry("engine_rpm",              new Range(600, 1050, 350, 1100)),
        Map.entry("catenary_voltage",        new Range(21, 29, 19, 31)),
        Map.entry("traction_motor_current",  new Range(0, 1000, -1, 1200)),
        Map.entry("dc_bus_voltage",          new Range(1600, 1900, 1400, 2000)),
        Map.entry("battery_voltage",         new Range(100, 120, 85, 130)),
        Map.entry("sand_level",              new Range(30, 100, 10, 100))
    );

    public double calculate(String paramName, double value) {
        Range r = RANGES.get(paramName);
        if (r == null) return -1;

        if (value >= r.normalMin() && value <= r.normalMax()) return 0.0;

        if (value < r.normalMin()) {
            if (r.critLow() < 0) return 0.0;
            double span = r.normalMin() - r.critLow();
            if (span <= 0) return 1.0;
            return Math.min(1.0, (r.normalMin() - value) / span);
        }

        double span = r.critHigh() - r.normalMax();
        if (span <= 0) return 1.0;
        return Math.min(1.0, (value - r.normalMax()) / span);
    }
}
