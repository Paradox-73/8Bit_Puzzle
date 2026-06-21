package com.eightbit.lb;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Consumes the puzzle-completed Redis Stream via a consumer group. Because it's a group, the
 * midnight burst queues durably: if this service is briefly swamped (or restarts) the stream
 * buffers and it catches up on un-acked messages. Nothing is lost (build doc section 4b).
 */
@Component
public class StreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(StreamConsumer.class);

    private final StringRedisTemplate redis;
    private final RedisConnectionFactory connectionFactory;
    private final Leaderboards leaderboards;

    @Value("${app.stream.key}")    private String streamKey;
    @Value("${app.stream.group}")  private String group;
    @Value("${app.stream.consumer}") private String consumer;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public StreamConsumer(StringRedisTemplate redis, RedisConnectionFactory connectionFactory,
                          Leaderboards leaderboards) {
        this.redis = redis;
        this.connectionFactory = connectionFactory;
        this.leaderboards = leaderboards;
    }

    @PostConstruct
    void start() {
        ensureGroup();

        var options = StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(group, consumer),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this::onMessage);
        container.start();
        log.info("Leaderboard consumer started: stream={} group={} consumer={}", streamKey, group, consumer);
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), group);
        } catch (Exception first) {
            // Stream may not exist yet -> create it with a noop record, then the group.
            if (Boolean.FALSE.equals(redis.hasKey(streamKey))) {
                try {
                    redis.opsForStream().add(StreamRecords.mapBacked(Map.of("init", "1")).withStreamKey(streamKey));
                } catch (Exception ignored) { }
            }
            try {
                redis.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), group);
            } catch (Exception ignored) {
                // BUSYGROUP: the group already exists -> fine.
            }
        }
    }

    private void onMessage(MapRecord<String, String, String> record) {
        Map<String, String> v = record.getValue();
        try {
            if (v.containsKey("userId")) {
                leaderboards.record(
                        v.get("type"),
                        LocalDate.parse(v.get("date")),
                        Long.parseLong(v.get("userId")),
                        v.getOrDefault("username", "player"),
                        Integer.parseInt(v.getOrDefault("batchYear", "0")),
                        Integer.parseInt(v.getOrDefault("score", "0")),
                        Boolean.parseBoolean(v.getOrDefault("verified", "true")));
            }
        } catch (Exception e) {
            log.warn("Skipping bad stream record {}: {}", record.getId(), e.getMessage());
        } finally {
            // Ack so it isn't redelivered. (A dead-letter/retry policy could be added later.)
            redis.opsForStream().acknowledge(streamKey, group, record.getId());
        }
    }

    @PreDestroy
    void stop() {
        if (container != null) container.stop();
    }
}
