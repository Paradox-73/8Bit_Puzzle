package com.eightbit.lb;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Redis sorted-set leaderboards. Identical maths to the monolith's version, but display names come
 * from a Redis hash populated at write time (from the event), so this service needs no database.
 */
@Service
public class Leaderboards {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MIN_PARTICIPANTS = 3;
    private static final Duration DAILY_TTL = Duration.ofDays(3);
    private static final int TOP_N = 100;
    private static final String USERMETA = "lb:usermeta"; // userId -> "username|batchYear"

    private final StringRedisTemplate redis;

    public Leaderboards(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String dailyCampus(String t, LocalDate d) { return "lb:" + t + ":" + d + ":campus"; }
    private String dailyBatch(String t, LocalDate d, int y) { return "lb:" + t + ":" + d + ":batch:" + y; }
    private String allTimeCampus(String t) { return "lb:" + t + ":alltime:campus"; }
    private String allTimeBatch(String t, int y) { return "lb:" + t + ":alltime:batch:" + y; }
    private String batchStats(String t, LocalDate d) { return "lb:" + t + ":" + d + ":batchstats"; }

    public void record(String type, LocalDate date, long userId, String username,
                       int batchYear, int score, boolean verified) {
        String uid = String.valueOf(userId);
        var z = redis.opsForZSet();

        // Campus boards count everyone.
        z.add(dailyCampus(type, date), uid, score);
        redis.expire(dailyCampus(type, date), DAILY_TTL);
        z.incrementScore(allTimeCampus(type), uid, score);

        // Batch boards + batch-war only count verified accounts.
        if (verified) {
            z.add(dailyBatch(type, date, batchYear), uid, score);
            redis.expire(dailyBatch(type, date, batchYear), DAILY_TTL);
            z.incrementScore(allTimeBatch(type, batchYear), uid, score);

            var h = redis.opsForHash();
            h.increment(batchStats(type, date), batchYear + ":sum", score);
            h.increment(batchStats(type, date), batchYear + ":count", 1);
            redis.expire(batchStats(type, date), DAILY_TTL);
        }

        // Display metadata (no DB needed for reads).
        redis.opsForHash().put(USERMETA, uid, username + "|" + batchYear);
    }

    public Map<String, Object> board(String type, String scope, String window, long userId, int callerBatch) {
        LocalDate date = LocalDate.now(ZONE);
        boolean batch = "batch".equalsIgnoreCase(scope);
        boolean allTime = "alltime".equalsIgnoreCase(window);
        String key = batch
                ? (allTime ? allTimeBatch(type, callerBatch) : dailyBatch(type, date, callerBatch))
                : (allTime ? allTimeCampus(type) : dailyCampus(type, date));

        Set<TypedTuple<String>> top = redis.opsForZSet().reverseRangeWithScores(key, 0, TOP_N - 1);
        List<Map<String, Object>> entries = new ArrayList<>();
        if (top != null) {
            int rank = 1;
            for (TypedTuple<String> t : top) {
                String uid = t.getValue();
                String[] meta = meta(uid);
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("rank", rank++);
                e.put("username", meta[0]);
                e.put("batchYear", meta[1].isEmpty() ? null : Integer.valueOf(meta[1]));
                e.put("score", t.getScore() == null ? 0 : t.getScore().intValue());
                entries.add(e);
            }
        }

        Map<String, Object> me = null;
        if (userId > 0) {
            Long myRank = redis.opsForZSet().reverseRank(key, String.valueOf(userId));
            Double myScore = redis.opsForZSet().score(key, String.valueOf(userId));
            if (myRank != null && myScore != null) {
                me = Map.of("rank", myRank + 1, "score", myScore.intValue());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", batch ? "batch" : "campus");
        out.put("window", allTime ? "alltime" : "daily");
        out.put("date", date.toString());
        out.put("entries", entries);
        out.put("me", me);
        return out;
    }

    public Map<String, Object> batchWar(String type) {
        LocalDate date = LocalDate.now(ZONE);
        Map<Object, Object> raw = redis.opsForHash().entries(batchStats(type, date));
        Map<Integer, long[]> agg = new HashMap<>();
        for (var en : raw.entrySet()) {
            String field = en.getKey().toString();
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
            long sum = en.getValue()[0], count = en.getValue()[1];
            double avg = count == 0 ? 0 : (double) sum / count;
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("batchYear", en.getKey());
            b.put("avgScore", Math.round(avg));
            b.put("players", count);
            b.put("totalScore", sum);
            batches.add(b);
            if (count >= MIN_PARTICIPANTS && avg > bestAvg) { bestAvg = avg; leader = en.getKey(); }
        }
        batches.sort((a, b) -> Long.compare((long) b.get("avgScore"), (long) a.get("avgScore")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date.toString());
        out.put("batches", batches);
        out.put("leader", leader);
        out.put("minParticipants", MIN_PARTICIPANTS);
        return out;
    }

    private String[] meta(String uid) {
        Object v = redis.opsForHash().get(USERMETA, uid);
        if (v == null) return new String[]{"player" + uid, ""};
        String s = v.toString();
        int i = s.lastIndexOf('|');
        return i < 0 ? new String[]{s, ""} : new String[]{s.substring(0, i), s.substring(i + 1)};
    }
}
