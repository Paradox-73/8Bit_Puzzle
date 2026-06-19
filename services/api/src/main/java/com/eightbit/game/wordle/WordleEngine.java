package com.eightbit.game.wordle;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Wordle rules. Server-authoritative: the answer never leaves this layer until the round
 * is over. Handles repeated letters correctly (the classic two-pass green-then-yellow algorithm).
 */
@Component
public class WordleEngine {

    public static final int WORD_LENGTH = 5;
    public static final int MAX_GUESSES = 6;

    public static final String GREEN = "GREEN";   // right letter, right spot
    public static final String YELLOW = "YELLOW"; // right letter, wrong spot
    public static final String GREY = "GREY";     // not in word (for remaining count)

    public List<String> score(String guess, String answer) {
        String g = guess.toUpperCase();
        String a = answer.toUpperCase();
        String[] result = new String[WORD_LENGTH];

        // Count letters in the answer not already matched green.
        Map<Character, Integer> remaining = new HashMap<>();

        // Pass 1: greens.
        for (int i = 0; i < WORD_LENGTH; i++) {
            char gc = g.charAt(i);
            if (gc == a.charAt(i)) {
                result[i] = GREEN;
            } else {
                remaining.merge(a.charAt(i), 1, Integer::sum);
            }
        }
        // Pass 2: yellows / greys.
        for (int i = 0; i < WORD_LENGTH; i++) {
            if (result[i] != null) continue;
            char gc = g.charAt(i);
            int left = remaining.getOrDefault(gc, 0);
            if (left > 0) {
                result[i] = YELLOW;
                remaining.put(gc, left - 1);
            } else {
                result[i] = GREY;
            }
        }
        return new ArrayList<>(List.of(result));
    }

    public boolean isSolved(List<String> pattern) {
        return pattern.stream().allMatch(GREEN::equals);
    }

    /**
     * Server-side scoring. Hard to game: the client never submits a score, only its moves.
     *  - base for solving
     *  - bonus for fewer guesses
     *  - small, CAPPED time bonus so a fast human is rewarded but a bot solving in 80ms gains nothing extra
     *  - streak multiplier, capped
     */
    public int score(boolean solved, int guessesUsed, long completionMs, int currentStreak) {
        if (!solved) return 0;
        int base = 1000;
        int guessBonus = (MAX_GUESSES - guessesUsed) * 120;     // 0..600

        // Reward solving within ~2 minutes, capped; floor at 0. No reward for sub-2s "solves".
        long seconds = Math.max(2, completionMs / 1000);
        int timeBonus = (int) Math.max(0, Math.min(120, (120 - seconds) * 2));

        double streakMult = Math.min(1.5, 1.0 + currentStreak * 0.02);
        return (int) Math.round((base + guessBonus + timeBonus) * streakMult);
    }
}
