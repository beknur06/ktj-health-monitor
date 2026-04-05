package kz.ktj.digitaltwin.healthindex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.ktj.digitaltwin.healthindex.dto.HealthIndexResult;
import kz.ktj.digitaltwin.healthindex.entity.HealthSnapshot;
import kz.ktj.digitaltwin.healthindex.repository.HealthSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthIndexPublisher {

    private static final Logger log = LoggerFactory.getLogger(HealthIndexPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HealthSnapshotRepository snapshotRepository;
    private final String channelPrefix;
    private final String keyPrefix;
    private final Duration ttl;
    private final int snapshotIntervalSeconds;

    private final ConcurrentHashMap<String, Instant> lastSnapshotTime = new ConcurrentHashMap<>();

    public HealthIndexPublisher(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            HealthSnapshotRepository snapshotRepository,
            @Value("${redis.channel.health:health}") String channelPrefix,
            @Value("${redis.key.health-prefix:health_index}") String keyPrefix,
            @Value("${redis.key.ttl-seconds:300}") int ttlSeconds,
            @Value("${health-index.snapshot-interval-seconds:10}") int snapshotIntervalSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.snapshotRepository = snapshotRepository;
        this.channelPrefix = channelPrefix;
        this.keyPrefix = keyPrefix;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
    }

    public void publish(HealthIndexResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            String locoId = result.getLocomotiveId();

            redis.opsForValue().set(keyPrefix + ":" + locoId, json, ttl);
            redis.convertAndSend(channelPrefix + ":" + locoId, json);
            persistSnapshotIfDue(result);

            log.debug("Published health index for {}: score={} category={} trend={}",
                locoId, result.getScore(), result.getCategory(), result.getTrend());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize health index: {}", e.getMessage());
        }
    }

    private void persistSnapshotIfDue(HealthIndexResult result) {
        String locoId = result.getLocomotiveId();
        Instant now = Instant.now();
        Instant lastTime = lastSnapshotTime.get(locoId);

        if (lastTime != null &&
            Duration.between(lastTime, now).getSeconds() < snapshotIntervalSeconds) {
            return;
        }

        try {
            HealthSnapshot snapshot = new HealthSnapshot();
            snapshot.setLocomotiveId(locoId);
            snapshot.setScore(result.getScore());
            snapshot.setCategory(HealthSnapshot.Category.valueOf(result.getCategory().name()));
            snapshot.setTopFactorsJson(objectMapper.writeValueAsString(result.getTopFactors()));
            snapshot.setCalculatedAt(result.getCalculatedAt());

            snapshotRepository.save(snapshot);
            lastSnapshotTime.put(locoId, now);

            log.trace("Persisted health snapshot for {}", locoId);

        } catch (Exception e) {
            log.error("Failed to persist health snapshot: {}", e.getMessage());
        }
    }
}
