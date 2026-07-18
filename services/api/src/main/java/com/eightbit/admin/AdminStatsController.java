package com.eightbit.admin;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.game.AttemptRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live-play stats for editors/admins — how the juniors are actually doing (solve-rate, avg time,
 * avg guesses per game, plus the busiest players). Gated to ROLE_EDITOR/ROLE_ADMIN via /admin/**.
 * This replaces the pre-launch trial dashboard that was removed with trial mode.
 */
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final AttemptRepository attempts;
    private final UserRepository users;

    public AdminStatsController(AttemptRepository attempts, UserRepository users) {
        this.attempts = attempts;
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> stats() {
        List<Map<String, Object>> byGame = new ArrayList<>();
        long totalFinished = 0, totalSolves = 0;
        for (Object[] r : attempts.liveStatsByGame()) {
            long att = num(r[1]);
            long solves = num(r[2]);
            totalFinished += att;
            totalSolves += solves;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("game", str(r[0]));
            m.put("attempts", att);
            m.put("solves", solves);
            m.put("solveRate", att == 0 ? 0 : Math.round(solves * 100.0 / att));
            m.put("avgMs", r[3] == null ? 0 : Math.round(((Number) r[3]).doubleValue()));
            m.put("avgMoves", r[4] == null ? 0 : Math.round(((Number) r[4]).doubleValue() * 10) / 10.0);
            m.put("players", num(r[5]));
            byGame.add(m);
        }

        List<Object[]> rows = attempts.liveTopPlayers();
        List<Long> ids = new ArrayList<>();
        for (Object[] r : rows) ids.add(num(r[0]));
        Map<Long, User> byId = new HashMap<>();
        users.findByIdIn(ids).forEach(u -> byId.put(u.getId(), u));

        List<Map<String, Object>> players = new ArrayList<>();
        for (Object[] r : rows) {
            User u = byId.get(num(r[0]));
            if (u == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("batchYear", u.getBatchYear());
            m.put("program", u.getProgram() == null ? "" : u.getProgram());
            m.put("attempts", num(r[1]));
            m.put("solves", num(r[2]));
            players.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalFinished", totalFinished);
        out.put("totalSolves", totalSolves);
        out.put("byGame", byGame);
        out.put("players", players);
        return out;
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
