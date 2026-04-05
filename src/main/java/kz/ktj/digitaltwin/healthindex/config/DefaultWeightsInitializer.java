package kz.ktj.digitaltwin.healthindex.config;

import jakarta.annotation.PostConstruct;
import kz.ktj.digitaltwin.healthindex.entity.HealthParamWeight;
import kz.ktj.digitaltwin.healthindex.repository.HealthParamWeightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultWeightsInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultWeightsInitializer.class);

    private final HealthParamWeightRepository repository;

    public DefaultWeightsInitializer(HealthParamWeightRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        List<HealthParamWeight> defaults = List.of(
            weight("coolant_temp",          "Охл. жидкость",         0.10, 1.8, 0.5, 0.8, "TE33A"),
            weight("oil_temp",              "Масло двигателя",       0.06, 1.5, 0.5, 0.8, "TE33A"),
            weight("exhaust_temp",          "Выхлопные газы",        0.06, 2.0, 0.5, 0.8, "TE33A"),
            weight("traction_motor_temp",   "Обмотки ТЭД",          0.08, 2.0, 0.5, 0.8, "BOTH"),
            weight("transformer_oil_temp",  "Масло трансформатора",  0.06, 1.5, 0.5, 0.8, "KZ8A"),
            weight("oil_pressure",          "Давление масла",        0.10, 2.5, 0.4, 0.7, "TE33A"),
            weight("brake_pipe_pressure",   "Торм. магистраль",      0.10, 2.5, 0.4, 0.7, "BOTH"),
            weight("main_reservoir_pressure","Главный резервуар",    0.05, 1.5, 0.5, 0.8, "BOTH"),
            weight("catenary_voltage",      "Напряжение сети",       0.08, 2.0, 0.4, 0.7, "KZ8A"),
            weight("traction_motor_current","Ток ТЭД",              0.05, 1.5, 0.6, 0.85,"BOTH"),
            weight("dc_bus_voltage",        "Шина DC",               0.04, 1.5, 0.5, 0.8, "KZ8A"),
            weight("battery_voltage",       "АКБ",                   0.03, 1.5, 0.5, 0.8, "BOTH"),
            weight("fuel_level",            "Уровень топлива",       0.08, 1.5, 0.5, 0.8, "TE33A"),
            weight("engine_rpm",            "Обороты дизеля",        0.04, 1.5, 0.5, 0.8, "TE33A"),
            weight("fuel_rate",             "Расход топлива",        0.03, 1.2, 0.6, 0.85,"TE33A"),
            weight("sand_level",            "Уровень песка",         0.05, 1.2, 0.5, 0.8, "BOTH"),
            weight("boost_pressure",        "Давление наддува",      0.05, 1.5, 0.5, 0.8, "TE33A")
        );

        Set<String> existing = repository.findAll().stream()
                .map(HealthParamWeight::getParamName)
                .collect(Collectors.toSet());

        List<HealthParamWeight> missing = defaults.stream()
                .filter(w -> !existing.contains(w.getParamName()))
                .collect(Collectors.toList());

        if (missing.isEmpty()) {
            log.info("Health param weights already fully configured ({} entries)", existing.size());
            return;
        }

        repository.saveAll(missing);
        log.info("Seeded {} missing health param weights (had {}, now {})",
                missing.size(), existing.size(), existing.size() + missing.size());
    }

    private HealthParamWeight weight(String param, String display, double w,
                                      double penalty, double warn, double crit,
                                      String applicableTo) {
        HealthParamWeight hp = new HealthParamWeight();
        hp.setParamName(param);
        hp.setDisplayName(display);
        hp.setWeight(w);
        hp.setPenaltyMultiplier(penalty);
        hp.setWarningThreshold(warn);
        hp.setCriticalThreshold(crit);
        hp.setApplicableTo(applicableTo);
        return hp;
    }
}
