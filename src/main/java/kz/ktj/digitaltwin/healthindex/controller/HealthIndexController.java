package kz.ktj.digitaltwin.healthindex.controller;

import kz.ktj.digitaltwin.healthindex.entity.HealthParamWeight;
import kz.ktj.digitaltwin.healthindex.entity.HealthSnapshot;
import kz.ktj.digitaltwin.healthindex.repository.HealthParamWeightRepository;
import kz.ktj.digitaltwin.healthindex.repository.HealthSnapshotRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/health")
public class HealthIndexController {

    private final HealthParamWeightRepository weightRepository;
    private final HealthSnapshotRepository snapshotRepository;

    public HealthIndexController(HealthParamWeightRepository weightRepository,
                                  HealthSnapshotRepository snapshotRepository) {
        this.weightRepository = weightRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @GetMapping("/config")
    public ResponseEntity<List<HealthParamWeight>> getConfig() {
        return ResponseEntity.ok(weightRepository.findAll());
    }

    @PutMapping("/config/{id}")
    public ResponseEntity<HealthParamWeight> updateWeight(
            @PathVariable java.util.UUID id,
            @RequestBody HealthParamWeight update) {
        return weightRepository.findById(id)
            .map(existing -> {
                existing.setWeight(update.getWeight());
                existing.setPenaltyMultiplier(update.getPenaltyMultiplier());
                existing.setWarningThreshold(update.getWarningThreshold());
                existing.setCriticalThreshold(update.getCriticalThreshold());
                existing.setDisplayName(update.getDisplayName());
                return ResponseEntity.ok(weightRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{locomotiveId}/history")
    public ResponseEntity<List<HealthSnapshot>> getHistory(
            @PathVariable String locomotiveId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<HealthSnapshot> snapshots = snapshotRepository
            .findByLocomotiveIdAndCalculatedAtBetweenOrderByCalculatedAtAsc(locomotiveId, from, to);
        return ResponseEntity.ok(snapshots);
    }

    @GetMapping("/{locomotiveId}/latest")
    public ResponseEntity<List<HealthSnapshot>> getLatest(@PathVariable String locomotiveId) {
        return ResponseEntity.ok(
            snapshotRepository.findTop30ByLocomotiveIdOrderByCalculatedAtDesc(locomotiveId));
    }
}
