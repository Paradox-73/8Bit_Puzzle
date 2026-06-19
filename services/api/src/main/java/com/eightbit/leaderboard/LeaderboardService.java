package com.eightbit.leaderboard;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.game.GameService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Redis Sorted Sets are the whole reason leaderboards stay fast under the midnight spike:
 * ZADD to insert, ZREVRANGE for the top 100, ZREVRANK for "your rank" -- all sub-millisecond,
 * no heavy ORDER BY over Postgres during the burst (build doc section 4c / 8).
 */
@Service
public class LeaderboardService {

    /** Rank batches by AVERAGE score, with a floor so one tryhard can't carry an empty batch. */
    private static final int MIN_PARTICIPANTS = 3;
    private static final Duration DAILY_TTL = Duration.ofDays(3);
    private static final int TOP_N = 100;

    private final StringRedisTemplate redis;
    private final UserRepository users;

    public LeaderboardService(StringRedisTemplate redis, UserRepository users) {
        this.redis = redis;
        this.users = users;
    }

    // --- key builders ---
    private String dailyCampus(String type, LocalDate d) { return "lb:" + type + ":" + d + ":campus"; }
    private String dailyBatch(String type, LocalDate d, int year) { return "lb:" + type + ":" + d + ":batch:" + year; }
    private String allTimeCampus(String type) { return "lb:" + type + ":alltime:campus"; }
    private String allTimeBatch(String type, int year) { return "lb:" + type + ":alltime:batch:" + year; }
    private String batchStats(String type, LocalDate d) { return "lb:" + type + ":" + d + ":batchstats"; }

    /** Called by the leaderboard listener after a puzzle is completed. */
    public void record(String type, LocalDate date, long userId, int batchYear, int score) {
        String uid = String.valueOf(userId);
        var z = redis.opsForZSet();

        // Daily boards: the player has exactly one attempt, so a plain ZADD is correct.
        z.add(dailyCampus(type, date), uid, score);
        z.add(dailyBatch(type, date, batchYear), uid, score);
        redis.expire(dailyCampus(type, date), DAILY_TTL);
        redis.expire(dailyBatch(type, date, batchYear), DAILY_TTL);

        // All-time boards accumulate.
        z.incrementScore(allTimeCampus(type), uid, score);
        z.incrementScore(allTimeBatch(type, batchYear), uid, score);

        // Batch-war aggregates: O(1) running sum + count per batch.
        var h = redis.opsForHash();
        h.increment(batchStats(type, date), batchYear + ":sum", score);
        h.increment(batchStats(type, date), batchYear + ":count", 1);
        redis.expire(batchStats(type, date), DAILY_TTL);
    }

    public Map<String, Object> board(String type, String scope, String window, long userId, int callerBatch) {
        LocalDate date = LocalDate.now(GameService.ZONE);
        boolean batch = "batch".equalsIgnoreCase(scope);
        boolean allTime = "alltime".equalsIgnoreCase(window);

        String key = batch
                ? (allTime ? allTimeBatch(type, callerBatch) : dailyBatch(type, date, callerBatch))
                : (allTime ? allTimeCampus(type) : dailyCampus(type, date));

        Set<TypedTuple<String>> top = redis.opsForZSet().reverseRangeWithScores(key, 0, TOP_N - 1);
        List<Map<String, Object>> entries = new ArrayList<>();
        if (top != null && !top.isEmpty()) {
            List<Long> ids = top.stream().map(t -> Long.parseLong(Objects.requireNonNull(t.getValue()))).toList();
            Map<Long, User> byId = new HashMap<>();
            users.findByIdIn(ids).forEach(u -> byId.put(u.getId(), u));
            int rank = 1;
            for (TypedTuple<String> t : top) {
                long id = Long.parseLong(Objects.requireNonNull(t.getValue()));
                User u = byId.get(id);
                if (u == null) continue;
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("rank", rank++);
                e.put("username", u.getUsername());
                e.put("batchYear", u.getBatchYear());
                e.put("score", t.getScore() == null ? 0 : t.getScore().intValue());
                entries.add(e);
            }
        }

        Map<String, Object> me = null;
        Long myRank = redis.opsForZSet().reverseRank(key, String.valueOf(userId));
        Double myScore = redis.opsForZSet().score(key, String.valueOf(userId));
        if (myRank != null && myScore != null) {
            me = Map.of("rank", myRank + 1, "score", myScore.intValue());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", batch ? "batch" : "campus");
        out.put("window", allTime ? "alltime" : "daily");
        out.put("date", date.toString());
        out.put("entries", entries);
        out.put("me", me);
        return out;
    }

    /** The homepage hero: average score per participating player, per batch. */
    public Map<String, Object> batchWar(String type) {
        LocalDate date = LocalDate.now(GameService.ZONE);
        Map<Object, Object> raw = redis.opsForHash().entries(batchStats(type, date));

        Map<Integer, long[]> agg = new HashMap<>(); // year -> [sum, count]
        for (var en : raw.entrySet()) {
            String field = en.getKey().toString();        // "2023:sum" / "2023:count"
            int sep = field.indexOf(':');
            int year = Integer.parseInt(field.substring(0, sep));
            String which = field.substring(sep + 1);
            long val = Long.parseLong(en.getValue().toString());
            long[] sc = agg.computeIfAbsent(year, k -> new long[2]);
            if (which.equals("sum")) sc[0] = val; else sc[1] = val;
        }

        List<Map<String, Object>> batches = new ArrayList<>();
        Integer leader = null;
        double bestAvg = -1;
        for (var en : agg.entrySet()) {
            long sum = en.getValue()[0];
            long count = en.getValue()[1];
            double avg = count == 0 ? 0 : (double) sum / count;
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("batchYear", en.getKey());
            b.put("avgScore", Math.round(avg));
            b.put("players", count);
            b.put("totalScore", sum);
            batches.add(b);
            if (count >= MIN_PARTICIPANTS && avg > bestAvg) {
                bestAvg = avg;
                leader = en.getKey();
            }
        }
        batches.sort((a, b) -> Long.compare((long) b.get("avgScore"), (long) a.get("avgScore")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date.toString());
        out.put("batches", batches);
        out.put("leader", leader);
        out.put("minParticipants", MIN_PARTICIPANTS);
        return out;
    }
}
