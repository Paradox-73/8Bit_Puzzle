package com.eightbit.game.play;

import com.eightbit.common.web.ApiException;
import com.eightbit.game.Attempt;
import com.eightbit.game.Puzzle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MinuteCryptic-style daily cryptic clue. One clue, one answer. The player submits guesses; three
 * progressive hints (definition, indicator, fodder) can be revealed on demand, and the full parse
 * (device + explanation) is shown only once the round is over.
 *
 * Server-authoritative: the answer/explanation never reach the client until revealed via a hint
 * (def/indicator/fodder only — never the answer) or until the round ends. Reuses the shared Attempt
 * row — each guess is stored verbatim and correctness is recomputed from the answer.
 */
@Component
public class CrypticPlay implements GamePlay {

    public static final int MAX_GUESSES = 6;
    private static final Set<String> HINT_KINDS = Set.of("definition", "indicator", "fodder");

    @Override
    public String type() {
        return "cryptic";
    }

    private String content(Puzzle p, String key) {
        Object v = p.getContent().get(key);
        return v == null ? "" : v.toString();
    }

    /** Compare ignoring case, spaces and punctuation, so "ICE CREAM" == "icecream". */
    private String normalize(String s) {
        return s == null ? "" : s.toUpperCase().replaceAll("[^A-Z]", "");
    }

    private List<Map<String, Object>> history(Puzzle p, Attempt attempt) {
        String answer = normalize(p.answer());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (attempt != null) {
            for (String g : attempt.getGuesses()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("guess", g);
                row.put("correct", normalize(g).equals(answer));
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Map<String, Object> todayView(Puzzle p, Attempt attempt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("config", Map.of("maxGuesses", MAX_GUESSES));
        view.put("clue", content(p, "clue"));
        view.put("enumeration", content(p, "enumeration"));
        view.put("guesses", history(p, attempt));
        view.put("hints", attempt == null ? List.of() : attempt.getHints());
        return view;
    }

    @Override
    public MoveStep step(Puzzle p, Attempt attempt, Map<String, Object> move) {
        Object raw = move.get("guess");
        String guess = raw == null ? "" : raw.toString().trim();
        String norm = normalize(guess);
        if (norm.isEmpty()) {
            throw ApiException.badRequest("EMPTY_GUESS", "Type your answer");
        }
        String answer = normalize(p.answer());
        if (norm.length() != answer.length()) {
            throw ApiException.badRequest("BAD_LENGTH", "Answer has " + answer.length() + " letters");
        }
        attempt.getGuesses().add(guess.toUpperCase());
        int used = attempt.getGuesses().size();
        boolean solved = norm.equals(answer);
        boolean gameOver = solved || used >= MAX_GUESSES;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("guessNumber", used);
        resp.put("correct", solved);
        resp.put("guessesUsed", used);
        resp.put("maxGuesses", MAX_GUESSES);
        return new MoveStep(solved, gameOver, resp);
    }

    /** Reveal one of the three clue parts. Never reveals the answer itself; capped at one per kind. */
    @Override
    public Map<String, Object> hint(Puzzle p, Attempt attempt, String kind) {
        String k = kind == null ? "" : kind.trim().toLowerCase();
        if (!HINT_KINDS.contains(k)) {
            throw ApiException.badRequest("BAD_HINT_KIND", "Hint must be 'definition', 'indicator' or 'fodder'");
        }
        for (Map<String, Object> h : attempt.getHints()) {
            if (k.equals(h.get("kind"))) return h;
        }
        String text = content(p, k);
        if (text.isBlank()) {
            throw ApiException.badRequest("NO_HINT", "No " + k + " hint for today's clue");
        }
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("kind", k);
        hint.put("text", text);
        attempt.getHints().add(hint);
        return hint;
    }

    @Override
    public int score(Puzzle p, Attempt a, int currentStreak) {
        if (!Boolean.TRUE.equals(a.getSolved())) return 0;
        int used = a.getGuesses().size();
        int base = 1000;
        int guessBonus = (MAX_GUESSES - used) * 120;   // fewer tries = better; no time component
        double streakMult = Math.min(1.5, 1.0 + currentStreak * 0.02);
        return (int) Math.round((base + guessBonus) * streakMult);
    }

    @Override
    public String shareGrid(Puzzle p, Attempt a) {
        boolean solved = Boolean.TRUE.equals(a.getSolved());
        int used = a.getGuesses().size();
        StringBuilder sb = new StringBuilder();
        String n = solved ? String.valueOf(used) : "X";
        sb.append("8Bit Cryptic • IIITB • ").append(n).append("/").append(MAX_GUESSES).append("\n");
        for (int i = 0; i < used; i++) {
            boolean last = i == used - 1;
            sb.append(solved && last ? "🟦" : "⬛");
        }
        return sb.toString().trim();
    }

    @Override
    public Map<String, Object> reveal(Puzzle p, Attempt a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", p.answer());
        m.put("enumeration", content(p, "enumeration"));
        m.put("definition", content(p, "definition"));
        m.put("indicator", content(p, "indicator"));
        m.put("fodder", content(p, "fodder"));
        m.put("device", content(p, "device"));
        m.put("explanation", content(p, "explanation"));
        return m;
    }
}
