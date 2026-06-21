package com.eightbit.lb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone leaderboard service (Phase 6 extraction). Consumes the puzzle-completed Redis Stream
 * written by the game service and owns the leaderboard read API. No Postgres: display names ride
 * along in the events and are kept in a Redis hash. Two tuned JVMs fit Oracle's 12 GB box.
 */
@SpringBootApplication
public class LeaderboardServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LeaderboardServiceApplication.class, args);
    }
}
