package com.eightbit.leaderboard.sink;

import com.eightbit.game.event.PuzzleCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stream sink: XADD the event to a Redis Stream. The standalone leaderboard service consumes it via
 * a consumer group, so the midnight burst queues durably and the game JVM never blocks on ranking.
 */
@Component
@ConditionalOnProperty(name = "app.leaderboard.sink", havingValue = "stream")
public class StreamLeaderboardSink implements LeaderboardSink {

    public static final String STREAM_KEY = "stream:puzzle-completed";
    private static final Logger log = LoggerFactory.getLogger(StreamLeaderboardSink.class);

    private final StringRedisTemplate redis;

    public StreamLeaderboardSink(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void publish(PuzzleCompletedEvent e) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("type", e.gameType());
        body.put("date", e.puzzleDate().toString());
        body.put("userId", String.valueOf(e.userId()));
        body.put("username", e.username());
        body.put("batchYear", String.valueOf(e.batchYear()));
        body.put("score", String.valueOf(e.score()));
        body.put("solved", String.valueOf(e.solved()));
        body.put("verified", String.valueOf(e.verified()));
        redis.opsForStream().add(StreamRecords.mapBacked(body).withStreamKey(STREAM_KEY));
        log.debug("XADD {} for user {}", STREAM_KEY, e.userId());
    }
}
