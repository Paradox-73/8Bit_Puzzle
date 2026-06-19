package com.eightbit.game.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public class GameDtos {

    /** One played row: the guessed word plus its colour pattern (recomputed server-side). */
    public record GuessRow(String guess, List<String> result) {}

    public record TodayResponse(
            Long puzzleId,
            String gameType,
            LocalDate date,
            Short difficulty,
            Config config,
            String status,            // NOT_STARTED | IN_PROGRESS | SOLVED | FAILED
            List<GuessRow> guesses,   // restored board; never contains the answer
            Integer score,            // when finished
            String answer             // only when finished (SOLVED/FAILED)
    ) {}

    public record Config(int maxGuesses, int wordLength) {}

    public record GuessRequest(@NotBlank String guess) {}

    public record GuessResponse(
            int guessNumber,
            List<String> result,
            boolean solved,
            boolean gameOver,
            int guessesUsed,
            int maxGuesses,
            String status,
            Integer score,        // only when gameOver
            String answer,        // only when gameOver
            String shareGrid      // only when gameOver
    ) {}

    public record AttemptSummary(
            Long puzzleId,
            String gameType,
            LocalDate date,
            Boolean solved,
            Integer score,
            int guessesUsed
    ) {}
}
