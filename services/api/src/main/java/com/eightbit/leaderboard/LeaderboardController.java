package com.eightbit.leaderboard;

import com.eightbit.common.security.AuthUser;
import org.springframework.security.core.Authentication;
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
    public Map<String, Object> batchWar(@RequestParam(defaultValue = "all") String type,
                                        @AuthenticationPrincipal AuthUser user,
                                        Authentication auth) {
        int viewerYear = user == null || user.batchYear() == null ? 0 : user.batchYear();
        // Admins/editors see every batch's standings across all years (viewerYear 0 = show all
        // cohorts), not just their own year's cohorts.
        if (isAdmin(auth)) viewerYear = 0;
        return service.batchWar(safeType(type), viewerYear);
    }

    /** True if the authenticated caller has an editor/admin role (authorities come from the JWT). */
    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_EDITOR".equals(a.getAuthority()));
    }
}
