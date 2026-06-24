package com.eightbit.game;

import com.eightbit.common.config.AppProperties;
import com.eightbit.game.wordle.WordList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the pre-launch trial in sync with the under-review puzzle file (puzzles-review.json). Each
 * dated entry becomes up to three status='trial' rows (wordle / connections / cryptic) served through
 * the normal game engine. The file is re-read whenever its last-modified time changes, so editing the
 * JSON shows up in the app on the next request — nothing is hardcoded. Upsert is keyed by
 * (gameType, date) so content tweaks keep stable puzzle ids and don't reset players' progress.
 *
 * Entirely part of trial mode; remove with it. Repository batch ops (saveAll/deleteAll) are each
 * atomic, so no surrounding transaction is needed.
 */
@Component
public class TrialPuzzleSync {

    private static final Logger log = LoggerFactory.getLogger(TrialPuzzleSync.class);
    private static final List<String> TYPES = List.of("wordle", "connections", "cryptic");

    private final PuzzleRepository puzzles;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final WordList wordList;

    private volatile long lastModified = Long.MIN_VALUE;
    private volatile boolean missingLogged = false;

    public TrialPuzzleSync(PuzzleRepository puzzles, AppProperties props, ObjectMapper mapper,
                           WordList wordList) {
        this.puzzles = puzzles;
        this.props = props;
        this.mapper = mapper;
        this.wordList = wordList;
    }

    private File file() {
        return new File(props.getTrial().getPuzzlesFile());
    }

    /** Cheap last-modified check; re-imports only when the file changed. Safe to call per request. */
    public void syncIfChanged() {
        File f = file();
        if (!f.isFile()) {
            if (!missingLogged) {
                log.warn("Trial puzzles file not found at {} — set app.trial.puzzles-file / TRIAL_PUZZLES_FILE",
                        f.getAbsolutePath());
                missingLogged = true;
            }
            return;
        }
        missingLogged = false;
        long m = f.lastModified();
        if (m == lastModified) return;
        doSync(f, m);
    }

    /** Force a re-import regardless of mtime (admin "Sync" button). Returns trial puzzles written. */
    public int forceSync() {
        File f = file();
        if (!f.isFile()) return 0;
        return doSync(f, f.lastModified());
    }

    /** Drop the cached mtime so the next request re-imports (used after a trial reset). */
    public void invalidate() {
        lastModified = Long.MIN_VALUE;
    }

    private synchronized int doSync(File f, long mtime) {
        try {
            JsonNode arr = mapper.readTree(f).get("puzzles");
            if (arr == null || !arr.isArray()) {
                log.warn("Trial sync: no 'puzzles' array in {}", f.getName());
                return 0;
            }

            Map<String, Puzzle> existing = new HashMap<>();
            for (Puzzle p : puzzles.findByStatus(PuzzleStatus.TRIAL)) {
                existing.put(p.getGameType() + "|" + p.getPublishDate(), p);
            }

            Set<String> seen = new HashSet<>();
            List<Puzzle> toSave = new ArrayList<>();
            for (JsonNode entry : arr) {
                JsonNode dateNode = entry.get("date");
                if (dateNode == null || dateNode.asText().isBlank()) continue;
                LocalDate date = LocalDate.parse(dateNode.asText());
                Short diff = entry.hasNonNull("difficulty") ? (short) entry.get("difficulty").asInt() : null;

                for (String type : TYPES) {
                    JsonNode node = entry.get(type);
                    Map<String, Object> content = contentFor(type, node);
                    if (content == null) continue;
                    // Make the wordle answer itself a legal guess (e.g. IIITB isn't in the dictionary).
                    if ("wordle".equals(type)) wordList.register((String) content.get("answer"));
                    String key = type + "|" + date;
                    seen.add(key);
                    Puzzle p = existing.get(key);
                    if (p == null) {
                        p = new Puzzle();
                        p.setGameType(type);
                        p.setPublishDate(date);
                        p.setStatus(PuzzleStatus.TRIAL);
                    }
                    p.setDifficulty(diff);
                    p.setContent(content);
                    toSave.add(p);
                }
            }
            if (!toSave.isEmpty()) puzzles.saveAll(toSave);

            List<Puzzle> stale = new ArrayList<>();
            existing.forEach((k, v) -> { if (!seen.contains(k)) stale.add(v); });
            if (!stale.isEmpty()) puzzles.deleteAll(stale);

            lastModified = mtime;
            log.info("Trial sync from {}: {} puzzles upserted, {} pruned", f.getName(), toSave.size(), stale.size());
            return toSave.size();
        } catch (Exception e) {
            log.error("Trial puzzle sync failed: {}", e.toString());
            return 0;
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
                addFlag(m, node);
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

    /** Carry the campusWord flag (answer isn't a normal dictionary word) so the UI can badge it. */
    private void addFlag(Map<String, Object> content, JsonNode node) {
        if (node.path("campusWord").asBoolean(false)) {
            content.put("campusWord", true);
        }
    }
}
