package com.eightbit.leaderboard;

import com.eightbit.common.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
public class LeaderboardController {

    // Whitelist so a caller can't inject arbitrary Redis key fragments via ?type=.
    private static final Set<String> TYPES = Set.of("all", "wordle", "connections", "cryptic");

    private final LeaderboardService service;

    public LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    private static String safeType(String t) {
        return t != null && TYPES.contains(t.toLowerCase()) ? t.toLowerCase() : "all";
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "campus") String scope,
            @RequestParam(defaultValue = "daily") String window,
            @AuthenticationPrincipal AuthUser user) {
        long userId = user == null ? -1 : user.id();
        int batch = user == null || user.batchYear() == null ? 0 : user.batchYear();
        return service.board(safeType(type), scope, window, userId, batch);
    }

    @GetMapping("/leaderboard/batch-war")
    public Map<String, Object> batchWar(@RequestParam(defaultValue = "all") String type) {
        return service.batchWar(safeType(type));
    }
}
