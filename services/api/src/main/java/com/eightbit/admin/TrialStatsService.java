package com.eightbit.admin;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.common.config.AppProperties;
import com.eightbit.game.AttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the playtest dashboard numbers and snapshots them to a file. Two strictly separate views:
 * {@code trial=true} = pre-launch playtesters, {@code trial=false} = real junior play after launch —
 * so the editor can compare "how testers did" with "how the juniors are actually playing".
 *
 * Snapshots are appended to a JSON file so the trial numbers survive the pre-launch data purge.
 */
@Service
public class TrialStatsService {

    private static final Logger log = LoggerFactory.getLogger(TrialStatsService.class);

    private final AttemptRepository attempts;
    private final UserRepository users;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public TrialStatsService(AttemptRepository attempts, UserRepository users,
                             AppProperties props, ObjectMapper mapper) {
        this.attempts = attempts;
        this.users = users;
        this.props = props;
        this.mapper = mapper;
    }

    /** Aggregated stats for one population. trial=true → playtest; trial=false → real junior play. */
    public Map<String, Object> build(boolean trial) {
        long totalAttempts = 0, totalSolves = 0;

        // Average ratings only exist for the trial (juniors don't get the rating widget).
        Map<String, Double> ratingByKey = new HashMap<>();
        if (trial) {
            for (Object[] r : attempts.trialRatingsByDifficulty()) {
                ratingByKey.put(r[0] + "|" + r[1], r[2] == null ? null : num(r[2]).doubleValue());
            }
        }

        List<Map<String, Object>> byDifficulty = new ArrayList<>();
        for (Object[] r : attempts.statsByDifficulty(trial)) {
            long a = num(r[2]).longValue();
            long s = num(r[3]).longValue();
            totalAttempts += a;
            totalSolves += s;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gameType", r[0]);
            m.put("difficulty", r[1] == null ? null : num(r[1]).intValue());
            m.put("attempts", a);
            m.put("solves", s);
            m.put("solveRate", a == 0 ? 0 : Math.round(s * 100.0 / a));
            m.put("avgMs", r[4] == null ? null : num(r[4]).longValue());
            m.put("avgGuesses", r[5] == null ? null : Math.round(num(r[5]).doubleValue() * 10) / 10.0);
            Double rating = ratingByKey.get(r[0] + "|" + r[1]);
            m.put("avgRating", rating == null ? null : Math.round(rating * 10) / 10.0);
            byDifficulty.add(m);
        }

        List<Map<String, Object>> byPlayer = new ArrayList<>();
        for (Object[] r : attempts.statsByUser(trial)) {
            long uid = num(r[0]).longValue();
            User u = users.findById(uid).orElse(null);
            long a = num(r[1]).longValue();
            long s = num(r[2]).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("username", u == null ? null : u.getUsername());
            m.put("rollNumber", u == null ? null : u.getRollNumber());
            m.put("attempts", a);
            m.put("solves", s);
            m.put("solveRate", a == 0 ? 0 : Math.round(s * 100.0 / a));
            m.put("totalMs", r[3] == null ? null : num(r[3]).longValue());
            m.put("lastAt", r[4] == null ? null : r[4].toString());
            byPlayer.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", trial ? "trial" : "live");
        out.put("players", byPlayer.size());
        out.put("totalAttempts", totalAttempts);
        out.put("totalSolves", totalSolves);
        out.put("solveRate", totalAttempts == 0 ? 0 : Math.round(totalSolves * 100.0 / totalAttempts));
        out.put("byDifficulty", byDifficulty);
        out.put("byPlayer", byPlayer);
        if (trial) out.put("comments", comments());
        return out;
    }

    private List<Map<String, Object>> comments() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : attempts.trialComments()) {
            User u = users.findById(num(r[0]).longValue()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u == null ? null : u.getUsername());
            m.put("gameType", r[1]);
            m.put("date", r[2] == null ? null : r[2].toString());
            m.put("difficulty", r[3] == null ? null : num(r[3]).intValue());
            m.put("rating", r[4] == null ? null : num(r[4]).intValue());
            m.put("message", r[5]);
            out.add(m);
        }
        return out;
    }

    /**
     * Append a timestamped snapshot of the current trial stats to the stats file, so the playtest
     * numbers are preserved even after the trial data is purged. Returns the file path + snapshot count.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> export() {
        File f = new File(props.getTrial().getStatsFile());
        List<Object> snapshots = new ArrayList<>();
        try {
            if (f.isFile() && f.length() > 0) {
                Object existing = mapper.readValue(f, Object.class);
                if (existing instanceof List<?> l) snapshots.addAll((List<Object>) l);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("exportedAt", Instant.now().toString());
            snapshot.put("stats", build(true));
            snapshots.add(snapshot);
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, snapshots);
            log.info("Trial stats exported to {} ({} snapshots)", f.getAbsolutePath(), snapshots.size());
            return Map.of("path", f.getAbsolutePath(), "snapshots", snapshots.size());
        } catch (Exception e) {
            log.error("Trial stats export failed: {}", e.toString());
            return Map.of("error", e.getMessage() == null ? "export failed" : e.getMessage());
        }
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }
}
