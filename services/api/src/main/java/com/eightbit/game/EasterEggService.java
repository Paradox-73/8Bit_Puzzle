package com.eightbit.game;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the easter-egg triggers an editor configured on a puzzle (build doc 11a).
 * Schema in {@code puzzles.easter_eggs}:
 * <pre>
 * { "triggers": [
 *     { "match": "BYTES", "title": "...", "body": "..." },   // fires when this word/tile is played
 *     { "onSolve": true,  "title": "...", "body": "..." }     // fires when the puzzle is solved
 * ]}
 * </pre>
 */
@Service
public class EasterEggService {

    @SuppressWarnings("unchecked")
    public Map<String, Object> eval(Puzzle puzzle, Map<String, Object> move, boolean solved) {
        if (puzzle.getEasterEggs() == null) return null;
        Object raw = puzzle.getEasterEggs().get("triggers");
        if (!(raw instanceof List<?> triggers)) return null;

        List<String> played = playedTokens(move);
        for (Object o : triggers) {
            if (!(o instanceof Map<?, ?> t)) continue;
            Map<String, Object> trig = (Map<String, Object>) t;

            Object match = trig.get("match");
            if (match != null) {
                String want = match.toString().toUpperCase();
                if (played.stream().anyMatch(want::equals)) return payload(trig);
            }
            if (Boolean.TRUE.equals(trig.get("onSolve")) && solved) {
                return payload(trig);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> playedTokens(Map<String, Object> move) {
        List<String> out = new ArrayList<>();
        Object guess = move.get("guess");
        if (guess != null) out.add(guess.toString().toUpperCase());
        Object selection = move.get("selection");
        if (selection instanceof List<?> list) {
            list.forEach(x -> out.add(x.toString().toUpperCase()));
        }
        return out;
    }

    private Map<String, Object> payload(Map<String, Object> trig) {
        return Map.of(
                "title", String.valueOf(trig.getOrDefault("title", "Easter egg!")),
                "body", String.valueOf(trig.getOrDefault("body", "You found something hidden.")));
    }
}
