package com.eightbit.leaderboard;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.common.config.AppProperties;
import com.eightbit.game.AttemptRepository;
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
    private final AttemptRepository attempts;
    private final AppProperties props;

    public LeaderboardService(StringRedisTemplate redis, UserRepository users,
                              AttemptRepository attempts, AppProperties props) {
        this.redis = redis;
        this.users = users;
        this.attempts = attempts;
        this.props = props;
    }

    // --- key builders ---
    private String dailyCampus(String type, LocalDate d) { return "lb:" + type + ":" + d + ":campus"; }
    private String dailyBatch(String type, LocalDate d, String cohort) { return "lb:" + type + ":" + d + ":batch:" + cohort; }
    private String allTimeCampus(String type) { return "lb:" + type + ":alltime:campus"; }
    private String allTimeBatch(String type, String cohort) { return "lb:" + type + ":alltime:batch:" + cohort; }

    /** "My Batch" boards are scoped to the player's cohort = program + year (e.g. "BTech CSE|2026"). */
    private String cohortOf(long userId, int fallbackYear) {
        return users.findById(userId)
                .map(u -> (u.getProgram() == null ? "?" : u.getProgram()) + "|" + u.getBatchYear())
                .orElse("?|" + fallbackYear);
    }

    private String boardKey(String type, boolean batch, boolean allTime, LocalDate d, String cohort) {
        return batch
                ? (allTime ? allTimeBatch(type, cohort) : dailyBatch(type, d, cohort))
                : (allTime ? allTimeCampus(type) : dailyCampus(type, d));
    }

    /** Combined cross-game board: built by unioning the per-game boards on read (no backfill needed). */
    public static final String COMBINED = "all";
    private static final List<String> GAMES = List.of("wordle", "connections", "cryptic");
    private static final Duration COMBINED_TTL = Duration.ofSeconds(120);

    /** Called by the leaderboard listener after a puzzle is completed. */
    public void record(String type, LocalDate date, long userId, int batchYear, int score, boolean verified) {
        String uid = String.valueOf(userId);
        var z = redis.opsForZSet();

        // Campus boards count everyone who played.
        z.add(dailyCampus(type, date), uid, score);
        redis.expire(dailyCampus(type, date), DAILY_TTL);
        z.incrementScore(allTimeCampus(type), uid, score);

        // Batch boards only count verified accounts (anti batch-stuffing). Scoped to the cohort.
        if (verified) {
            String cohort = cohortOf(userId, batchYear);
            z.add(dailyBatch(type, date, cohort), uid, score);
            redis.expire(dailyBatch(type, date, cohort), DAILY_TTL);
            z.incrementScore(allTimeBatch(type, cohort), uid, score);
        }
    }

    public Map<String, Object> board(String type, String scope, String window, long userId, int callerBatch) {
        LocalDate date = LocalDate.now(GameService.ZONE);
        boolean batch = "batch".equalsIgnoreCase(scope);
        boolean allTime = "alltime".equalsIgnoreCase(window);
        String cohort = batch ? cohortOf(userId, callerBatch) : null;

        String key;
        if (COMBINED.equals(type)) {
            // Sum the three per-game boards on read, so "Total" reflects whatever games each player
            // has actually played (one game or all three) — with no separate combined writes/backfill.
            List<String> src = GAMES.stream()
                    .map(g -> boardKey(g, batch, allTime, date, cohort))
                    .toList();
            key = boardKey(COMBINED, batch, allTime, date, cohort);
            redis.opsForZSet().unionAndStore(src.get(0), src.subList(1, src.size()), key);
            redis.expire(key, COMBINED_TTL);
        } else {
            key = boardKey(type, batch, allTime, date, cohort);
        }

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

    /** The homepage hero: each cohort's participation % (distinct solvers ÷ capacity) for today,
     *  so small batches aren't out-muscled by big ones — it rewards getting your batch to play. */
    public Map<String, Object> batchWar(String type) {
        LocalDate date = LocalDate.now(GameService.ZONE);

        Map<String, Long> solvedBy = new HashMap<>(); // "program|year" -> distinct solvers today
        for (Object[] r : attempts.solversByCohort(date, type)) {
            String program = r[0] == null ? "" : r[0].toString();
            int year = ((Number) r[1]).intValue();
            long solvers = ((Number) r[2]).longValue();
            solvedBy.put(program + "|" + year, solvers);
        }

        List<Map<String, Object>> cohorts = new ArrayList<>();
        String leader = null;
        double bestPct = -1;
        for (AppProperties.BatchWar.Cohort c : props.getBatchWar().getCohorts()) {
            long solved = solvedBy.getOrDefault(c.getProgram() + "|" + c.getYear(), 0L);
            int cap = Math.max(1, c.getCapacity());
            int pct = (int) Math.min(100, Math.round((double) solved / cap * 100.0));
            String yy = String.valueOf(c.getYear());
            String label = c.getProgram() + " '" + (yy.length() >= 2 ? yy.substring(yy.length() - 2) : yy);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("program", c.getProgram());
            m.put("year", c.getYear());
            m.put("label", label);
            m.put("solvers", solved);
            m.put("capacity", c.getCapacity());
            m.put("pct", pct);
            cohorts.add(m);

            if (solved > 0 && pct > bestPct) {
                bestPct = pct;
                leader = label;
            }
        }
        cohorts.sort((a, b) -> Integer.compare((Integer) b.get("pct"), (Integer) a.get("pct")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date.toString());
        out.put("cohorts", cohorts);
        out.put("leader", leader);
        return out;
    }
}
