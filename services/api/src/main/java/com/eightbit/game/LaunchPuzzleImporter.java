package com.eightbit.game;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.game.wordle.WordList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time importer: publishes the dated puzzles in the review file (puzzles-review.json) as REAL
 * daily puzzles (status='published'), served one-per-day by the normal engine
 * ({@link PuzzleRepository#findServableForDate}). This replaced the old pre-launch trial sync — the
 * same gitignored file (so answers never reach the repo) is now the seed source for the live run
 * instead of a separate playtest pool.
 *
 * <p><b>Fill-if-missing:</b> a day/game that already has a scheduled or published puzzle is left
 * untouched, so the DB is the source of truth after the first import — admin edits and re-runs never
 * clobber or duplicate. Delete a day's puzzle and reboot to re-import it from the file.
 *
 * <p>Runs at startup; if the file is absent it logs and skips (the app still serves whatever is in
 * the DB / the evergreen failsafe pool).
 */
@Component
@Order(100) // ordering vs DataSeeder doesn't matter: author-id is optional and published rows aren't purged
public class LaunchPuzzleImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LaunchPuzzleImporter.class);
    private static final List<String> TYPES = List.of("wordle", "connections", "cryptic");
    private static final String EDITOR_USER = "editor";

    private final PuzzleRepository puzzles;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final WordList wordList;
    private final String puzzlesFile;

    public LaunchPuzzleImporter(PuzzleRepository puzzles, UserRepository users, ObjectMapper mapper,
                                WordList wordList,
                                @Value("${app.import.puzzles-file:puzzles-review.json}") String puzzlesFile) {
        this.puzzles = puzzles;
        this.users = users;
        this.mapper = mapper;
        this.wordList = wordList;
        this.puzzlesFile = puzzlesFile;
    }

    @Override
    @Transactional
    public void run(String... args) {
        File f = new File(puzzlesFile);
        if (!f.isFile()) {
            log.info("Puzzle import file not found at {} — skipping import (serving DB/evergreen only). "
                    + "Set app.import.puzzles-file / IMPORT_PUZZLES_FILE.", f.getAbsolutePath());
            return;
        }
        Long editorId = users.findByUsername(EDITOR_USER).map(User::getId).orElse(null);
        try {
            JsonNode arr = mapper.readTree(f).get("puzzles");
            if (arr == null || !arr.isArray()) {
                log.warn("Puzzle import: no 'puzzles' array in {}", f.getName());
                return;
            }
            int created = 0, skipped = 0;
            for (JsonNode entry : arr) {
                JsonNode dateNode = entry.get("date");
                if (dateNode == null || dateNode.asText().isBlank()) continue;
                LocalDate date = LocalDate.parse(dateNode.asText());
                Short diff = entry.hasNonNull("difficulty") ? (short) entry.get("difficulty").asInt() : null;

                for (String type : TYPES) {
                    Map<String, Object> content = contentFor(type, entry.get(type));
                    if (content == null) continue;
                    // Make the wordle answer a legal guess even if it isn't in the dictionary (IIITB, MAGGI…).
                    if ("wordle".equals(type)) wordList.register((String) content.get("answer"));
                    if (puzzles.findServableForDate(type, date).isPresent()) { skipped++; continue; }
                    Puzzle p = new Puzzle();
                    p.setGameType(type);
                    p.setPublishDate(date);
                    p.setDifficulty(diff);
                    p.setContent(content);
                    p.setStatus(PuzzleStatus.PUBLISHED);
                    p.setAuthorId(editorId);
                    p.setReviewerId(editorId);
                    puzzles.save(p);
                    created++;
                }
            }
            log.info("Puzzle import from {}: {} published, {} already present (kept)", f.getName(), created, skipped);
        } catch (Exception e) {
            log.error("Puzzle import failed: {}", e.toString());
        }
    }

    /** Map a per-game JSON node to the content shape the GamePlay strategies expect. */
    private Map<String, Object> contentFor(String type, JsonNode node) {
        if (node == null || node.isNull()) return null;
        switch (type) {
            case "wordle" -> {
                JsonNode a = node.get("answer");
                if (a == null || a.asText().isBlank()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("answer", a.asText().toUpperCase());
                if (node.path("campusWord").asBoolean(false)) m.put("campusWord", true);
                return m;
            }
            case "connections" -> {
                JsonNode groups = node.get("groups");
                if (groups == null || !groups.isArray()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("groups", mapper.convertValue(groups, List.class));
                return m;
            }
            case "cryptic" -> {
                if (node.get("answer") == null) return null;
                @SuppressWarnings("unchecked")
                Map<String, Object> m = mapper.convertValue(node, Map.class);
                Object ans = m.get("answer");
                if (ans != null) m.put("answer", ans.toString().toUpperCase());
                return m;
            }
            default -> {
                return null;
            }
        }
    }
}
