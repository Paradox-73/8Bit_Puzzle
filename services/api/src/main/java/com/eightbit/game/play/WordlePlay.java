package com.eightbit.game.play;

import com.eightbit.common.web.ApiException;
import com.eightbit.game.Attempt;
import com.eightbit.game.Puzzle;
import com.eightbit.game.wordle.WordList;
import com.eightbit.game.wordle.WordleEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WordlePlay implements GamePlay {

    private final WordleEngine engine;
    private final WordList wordList;

    public WordlePlay(WordleEngine engine, WordList wordList) {
        this.engine = engine;
        this.wordList = wordList;
    }

    @Override
    public String type() {
        return "wordle";
    }

    @Override
    public Map<String, Object> todayView(Puzzle p, Attempt attempt) {
        String answer = p.answer();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (attempt != null) {
            for (String g : attempt.getGuesses()) {
                rows.add(Map.of("guess", g, "result", engine.score(g, answer)));
            }
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("config", Map.of("maxGuesses", WordleEngine.MAX_GUESSES, "wordLength", WordleEngine.WORD_LENGTH));
        view.put("guesses", rows);
        return view;
    }

    @Override
    public MoveStep step(Puzzle p, Attempt attempt, Map<String, Object> move) {
        Object raw = move.get("guess");
        String guess = raw == null ? "" : raw.toString().trim().toUpperCase();
        if (guess.length() != WordleEngine.WORD_LENGTH) {
            throw ApiException.badRequest("BAD_LENGTH", "Guess must be 5 letters");
        }
        if (!wordList.isAllowed(guess)) {
            throw ApiException.badRequest("NOT_IN_WORD_LIST", "Not in word list");
        }
        List<String> pattern = engine.score(guess, p.answer());
        attempt.getGuesses().add(guess);
        int used = attempt.getGuesses().size();
        boolean solved = engine.isSolved(pattern);
        boolean gameOver = solved || used >= WordleEngine.MAX_GUESSES;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("guessNumber", used);
        resp.put("result", pattern);
        resp.put("guessesUsed", used);
        resp.put("maxGuesses", WordleEngine.MAX_GUESSES);
        return new MoveStep(solved, gameOver, resp);
    }

    @Override
    public int score(Puzzle p, Attempt a, int currentStreak) {
        boolean solved = Boolean.TRUE.equals(a.getSolved());
        long ms = a.getCompletionMs() == null ? 0 : a.getCompletionMs();
        return engine.score(solved, a.getGuesses().size(), ms, currentStreak);
    }

    @Override
    public String shareGrid(Puzzle p, Attempt a) {
        String answer = p.answer();
        boolean solved = Boolean.TRUE.equals(a.getSolved());
        StringBuilder sb = new StringBuilder();
        String n = solved ? String.valueOf(a.getGuesses().size()) : "X";
        sb.append("8Bit • IIITB • ").append(n).append("/").append(WordleEngine.MAX_GUESSES).append("\n");
        for (String g : a.getGuesses()) {
            for (String cell : engine.score(g, answer)) {
                sb.append(switch (cell) {
                    case WordleEngine.GREEN -> "🟦";  // blue square (8Bit uses blue, not green)
                    case WordleEngine.YELLOW -> "🟨"; // yellow square
                    default -> "⬛";                          // black square
                });
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public Map<String, Object> reveal(Puzzle p, Attempt a) {
        return Map.of("answer", p.answer());
    }
}
