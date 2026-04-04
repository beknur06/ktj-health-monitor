package kz.ktj.digitaltwin.healthindex.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

/**
 * Конфигурация весов параметров для расчёта индекса здоровья.
 * Хранится в PostgreSQL — можно менять без перекомпиляции.
 *
 * weight:            важность параметра (0..1, сумма всех = 1.0)
 * penaltyMultiplier: множитель штрафа при критическом отклонении
 * warningThreshold:  normalized deviation >= этого значения = WARNING
 * criticalThreshold: normalized deviation >= этого значения = CRITICAL
 */
@Entity
@Table(name = "health_param_weights")
@Data
public class HealthParamWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "paramName", nullable = false)
    private String paramName;

    @Column(name = "displayName", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private double weight;

    @Column(name = "penaltyMultiplier", nullable = false)
    private double penaltyMultiplier = 1.5;

    @Column(name = "warningThreshold", nullable = false)
    private double warningThreshold = 0.5;

    @Column(name = "criticalThreshold", nullable = false)
    private double criticalThreshold = 0.8;

    /** Применимо к KZ8A, TE33A, или обоим */
    @Column(name = "applicableTo", nullable = false)
    private String applicableTo = "BOTH";
}
