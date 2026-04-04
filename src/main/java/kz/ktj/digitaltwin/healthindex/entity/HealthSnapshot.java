package kz.ktj.digitaltwin.healthindex.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Снимок индекса здоровья, сохраняемый каждые N секунд.
 * Используется для исторических отчётов и трендов.
 */
@Entity
@Table(name = "health_snapshots", indexes = {
    @Index(name = "idx_snapshot_loco_time", columnList = "locomotiveId, calculatedAt DESC")
})
@Data
public class HealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "locomotiveId", nullable = false)
    private String locomotiveId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Category category;

    /** JSON массив top-5 факторов */
    @Column(name = "topFactorsJson", columnDefinition = "TEXT")
    private String topFactorsJson;

    @Column(name = "calculatedAt", nullable = false)
    private Instant calculatedAt;

    public enum Category {
        NORMAL, ATTENTION, CRITICAL
    }
}
