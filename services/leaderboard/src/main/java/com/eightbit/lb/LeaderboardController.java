package com.eightbit.lb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Same read API the monolith exposed at /leaderboard — now served by the standalone service.
 * In the microservices deployment the gateway routes /leaderboard/** here.
 */
@RestController
public class LeaderboardController {

    private final Leaderboards leaderboards;
    private final JwtReader jwt;

    public LeaderboardController(Leaderboards leaderboards, JwtReader jwt) {
        this.leaderboards = leaderboards;
        this.jwt = jwt;
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(
            @RequestParam(defaultValue = "wordle") String type,
            @RequestParam(defaultValue = "campus") String scope,
            @RequestParam(defaultValue = "daily") String window,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        JwtReader.Caller c = jwt.read(auth);
        return leaderboards.board(type, scope, window, c.userId(), c.batchYear());
    }

    @GetMapping("/leaderboard/batch-war")
    public Map<String, Object> batchWar(@RequestParam(defaultValue = "wordle") String type) {
        return leaderboards.batchWar(type);
    }
}
