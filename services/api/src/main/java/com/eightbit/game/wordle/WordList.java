package com.eightbit.game.wordle;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * The set of words a player is allowed to GUESS. Loaded once from
 * {@code resources/words/allowed.txt}. The answer itself comes from the puzzle, not this list,
 * but answers are also added so a scheduled answer is always guessable.
 */
@Component
public class WordList {

    private final Set<String> allowed = new HashSet<>();

    @PostConstruct
    void load() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("words/allowed.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String w = line.trim().toUpperCase();
                if (w.length() == 5 && w.chars().allMatch(Character::isLetter)) {
                    allowed.add(w);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load word list", e);
        }
    }

    public boolean isAllowed(String word) {
        return word != null && allowed.contains(word.toUpperCase());
    }

    /** Ensure a scheduled answer is always a legal guess even if it's missing from the list. */
    public void register(String word) {
        if (word != null && word.length() == 5) {
            allowed.add(word.toUpperCase());
        }
    }

    public int size() { return allowed.size(); }
}
