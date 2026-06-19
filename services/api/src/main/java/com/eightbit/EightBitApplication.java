package com.eightbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 8Bit Daily Puzzle - modular monolith.
 *
 * Internal modules (auth, game, leaderboard, profile, admin, notification, common) talk to each
 * other only through public services and Spring application events. The game module publishes a
 * {@code PuzzleCompletedEvent} and the leaderboard module listens for it -- an in-process seam
 * today, a cross-service message tomorrow (see build doc section 4).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EightBitApplication {
    public static void main(String[] args) {
        SpringApplication.run(EightBitApplication.class, args);
    }
}
