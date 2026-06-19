package com.eightbit.leaderboard;

import com.eightbit.common.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LeaderboardController {

    private final LeaderboardService service;

    public LeaderboardController(LeaderboardService service) {
        this.service = service;
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(
            @RequestParam(defaultValue = "wordle") String type,
            @RequestParam(defaultValue = "campus") String scope,
            @RequestParam(defaultValue = "daily") String window,
            @AuthenticationPrincipal AuthUser user) {
        long userId = user == null ? -1 : user.id();
        int batch = user == null || user.batchYear() == null ? 0 : user.batchYear();
        return service.board(type, scope, window, userId, batch);
    }

    @GetMapping("/leaderboard/batch-war")
    public Map<String, Object> batchWar(@RequestParam(defaultValue = "wordle") String type) {
        return service.batchWar(type);
    }
}
