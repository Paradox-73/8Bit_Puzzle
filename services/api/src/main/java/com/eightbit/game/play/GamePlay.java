package com.eightbit.game.play;

import com.eightbit.game.Attempt;
import com.eightbit.game.Puzzle;

import java.util.Map;

/**
 * A game "type" = a content schema + a server-side validator/scorer + a client view builder.
 * Adding a game means adding one implementation of this interface; auth, leaderboard, profile,
 * scheduling and notifications never change (build doc section 2).
 *
 * The answer/solution never leaves these implementations until the round is over.
 */
public interface GamePlay {

    /** The game_type code, e.g. "wordle", "connections". */
    String type();

    /** Game-specific fields for GET /puzzles/today (board state restored from the attempt). */
    Map<String, Object> todayView(Puzzle puzzle, Attempt attempt);

    /**
     * Validate and apply one move, appending it to {@code attempt.guesses}. Throws ApiException on
     * an invalid move (e.g. not a real word / bad selection).
     */
    MoveStep step(Puzzle puzzle, Attempt attempt, Map<String, Object> move);

    /** Server-side score, computed from the validated moves. The client never submits a score. */
    int score(Puzzle puzzle, Attempt attempt, int currentStreak);

    /** One-tap share text (emoji grid). */
    String shareGrid(Puzzle puzzle, Attempt attempt);

    /** Fields safe to reveal only once the round is over (e.g. the Wordle answer). */
    default Map<String, Object> reveal(Puzzle puzzle, Attempt attempt) {
        return Map.of();
    }

    /** Result of applying one move: game-specific response fields plus solved/gameOver flags. */
    record MoveStep(boolean solved, boolean gameOver, Map<String, Object> response) {}
}
