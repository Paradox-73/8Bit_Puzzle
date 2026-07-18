package com.eightbit.bootstrap;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.game.AttemptRepository;
import com.eightbit.game.GameType;
import com.eightbit.game.GameTypeRepository;
import com.eightbit.game.Puzzle;
import com.eightbit.game.PuzzleRepository;
import com.eightbit.game.PuzzleStatus;
import com.eightbit.game.wordle.WordList;
import com.eightbit.profile.UserStats;
import com.eightbit.profile.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the minimum needed to play immediately on a fresh database: game types, an editor account,
 * and an evergreen failsafe pool (so a day with no scheduled puzzle never 404s). Idempotent -- skips
 * anything that already exists.
 *
 * It also purges the hardcoded dated demo puzzles seeded by earlier builds (see
 * {@link #purgeLegacyDemoPuzzles()}): real content now comes from the admin CMS or the launch importer
 * ({@link com.eightbit.game.LaunchPuzzleImporter}). The purge runs every boot, even when seeding is disabled.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Demo editor identity. The password is env-overridable (SEED_EDITOR_PASSWORD) and seeding can
    // be turned off entirely in production (app.seed.enabled=false / SEED_ENABLED=false), so a
    // public repo never ships a working production editor login.
    private static final String EDITOR_ROLL = "IMT2023999";
    private static final String EDITOR_USER = "editor";
    // Passwordless login is by email, so the editor needs one. Used to log in as admin locally.
    private static final String EDITOR_EMAIL = "editor@iiitb.ac.in";

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    // No password committed. If SEED_EDITOR_PASSWORD is unset, a random one is generated on first
    // boot and printed once to the logs.
    @Value("${app.seed.editor-password:}")
    private String editorPassword;

    private static final List<String> EVERGREEN_WORDS = List.of(
            "MAGIC", "QUICK", "LIGHT", "SOUND", "EARTH", "MUSIC", "DREAM", "PEACE", "HAPPY", "SHINE");

    private final GameTypeRepository gameTypes;
    private final PuzzleRepository puzzles;
    private final AttemptRepository attempts;
    private final UserRepository users;
    private final UserStatsRepository stats;
    private final PasswordEncoder encoder;
    private final WordList wordList;

    public DataSeeder(GameTypeRepository gameTypes, PuzzleRepository puzzles, AttemptRepository attempts,
                      UserRepository users, UserStatsRepository stats, PasswordEncoder encoder,
                      WordList wordList) {
        this.gameTypes = gameTypes;
        this.puzzles = puzzles;
        this.attempts = attempts;
        this.users = users;
        this.stats = stats;
        this.encoder = encoder;
        this.wordList = wordList;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Runs unconditionally: clean up the old hardcoded dated demo puzzles even when seeding is off.
        purgeLegacyDemoPuzzles();
        if (!seedEnabled) {
            log.info("Seeding disabled (app.seed.enabled=false) -- skipping demo data");
            return;
        }
        seedGameTypes();
        seedEditor();
        seedPuzzles();
    }

    /**
     * Delete the dated demo puzzles seeded by earlier builds (the PIXEL/BYTES… Wordles, demo
     * Connections, LISTEN/HEART… cryptics). Their attempts go first (FK puzzle_id). The evergreen
     * failsafe pool is deliberately kept. No-op once nothing matches, so it's safe on every boot.
     */
    private void purgeLegacyDemoPuzzles() {
        List<Long> demoIds = puzzles.findSeededDemoIds();
        if (demoIds.isEmpty()) return;
        int attemptsDeleted = attempts.deleteByPuzzleIds(demoIds);
        puzzles.deleteAllById(demoIds);
        log.info("Purged {} legacy hardcoded demo puzzles ({} attempts removed)",
                demoIds.size(), attemptsDeleted);
    }

    private void seedGameTypes() {
        // Idempotent per-type so new game types (e.g. cryptic) are added even on an existing DB.
        ensureGameType("wordle", "Wordle", true);
        ensureGameType("connections", "Connections", true);
        ensureGameType("cryptic", "Minute Cryptic", true);
        ensureGameType("pixel", "Pixel Reveal", false);
        ensureGameType("cipher", "Cipher / Decode", false);
    }

    private void ensureGameType(String code, String displayName, boolean active) {
        if (!gameTypes.existsById(code)) {
            gameTypes.save(new GameType(code, displayName, active));
            log.info("Seeded game type '{}'", code);
        }
    }

    private void seedEditor() {
        if (users.existsByUsername(EDITOR_USER)) {
            // Backfill the login email on an editor seeded before passwordless auth.
            users.findByUsername(EDITOR_USER).ifPresent(e -> {
                if (e.getEmail() == null || e.getEmail().isBlank()) {
                    e.setEmail(EDITOR_EMAIL);
                    users.save(e);
                    log.info("Backfilled editor email -> {}", EDITOR_EMAIL);
                }
            });
            return;
        }
        String pass = (editorPassword == null || editorPassword.isBlank())
                ? generatePassword() : editorPassword;
        User e = new User();
        e.setRollNumber(EDITOR_ROLL);
        e.setUsername(EDITOR_USER);
        e.setEmail(EDITOR_EMAIL);
        e.setPasswordHash(encoder.encode(pass));
        e.setBatchYear(2023);
        e.setProgram("iMTech");
        e.setRoles("ROLE_USER,ROLE_EDITOR,ROLE_ADMIN");
        e = users.save(e);
        stats.save(new UserStats(e.getId()));
        log.warn("================ SEEDED EDITOR ACCOUNT ================");
        log.warn("  roll: {}   username: {}", EDITOR_ROLL, EDITOR_USER);
        log.warn("  password: {}", pass);
        log.warn("  (set SEED_EDITOR_PASSWORD to choose it, or SEED_ENABLED=false in prod)");
        log.warn("======================================================");
    }

    private String generatePassword() {
        // Random, URL-safe; printed once to the logs above.
        byte[] buf = new byte[9];
        new java.security.SecureRandom().nextBytes(buf);
        return "ed-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Evergreen failsafe pools only. These have no publish date, so they never appear on the admin
     * calendar and are served only when a day has no scheduled/published puzzle (so the game never
     * 404s). Real dated puzzles come from the admin CMS or the launch importer (puzzles-review.json).
     * Idempotent: skips a pool that already exists.
     */
    private void seedPuzzles() {
        Long editorId = users.findByUsername(EDITOR_USER).map(User::getId).orElse(null);

        if (puzzles.findEvergreen("wordle").isEmpty()) {
            for (String w : EVERGREEN_WORDS) {
                String word = w.toUpperCase();
                wordList.register(word);
                Puzzle p = new Puzzle();
                p.setGameType("wordle");
                p.setPublishDate(null);
                p.setDifficulty((short) 3);
                p.setContent(answer(word));
                p.setStatus(PuzzleStatus.EVERGREEN);
                p.setAuthorId(editorId);
                p.setReviewerId(editorId);
                puzzles.save(p);
            }
        }

        if (puzzles.findEvergreen("cryptic").isEmpty()) {
            Puzzle p = new Puzzle();
            p.setGameType("cryptic");
            p.setPublishDate(null);
            p.setDifficulty((short) 2);
            p.setContent(cryptic("LISTEN", "(6)", "Pay attention to broken tinsel (6)",
                    "Pay attention", "broken", "tinsel", "Anagram",
                    "LISTEN ('pay attention') is an anagram (‘broken’) of TINSEL."));
            p.setStatus(PuzzleStatus.EVERGREEN);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId);
            puzzles.save(p);
        }

        if (puzzles.findEvergreen("connections").isEmpty()) {
            Puzzle p = new Puzzle();
            p.setGameType("connections");
            p.setPublishDate(null);
            p.setDifficulty((short) 3);
            Map<String, Object> content = new HashMap<>();
            content.put("groups", List.of(
                    group(0, "Hostel life", "WIFI", "MESS", "LAUNDRY", "CURFEW"),
                    group(1, "Things in code", "PYTHON", "JAVA", "DEBUG", "COMMIT"),
                    group(2, "Spots on campus", "ATRIUM", "LIBRARY", "COURT", "GAZEBO"),
                    group(3, "___ test", "UNIT", "STRESS", "BETA", "ACID")));
            p.setContent(content);
            p.setStatus(PuzzleStatus.EVERGREEN);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId);
            puzzles.save(p);
        }
        log.info("Ensured evergreen failsafe pools (wordle + connections + cryptic)");
    }

    private Map<String, Object> answer(String word) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", word);
        return m;
    }

    private Map<String, Object> group(int level, String category, String... members) {
        Map<String, Object> g = new HashMap<>();
        g.put("level", level);
        g.put("category", category);
        g.put("members", List.of(members));
        return g;
    }

    private Map<String, Object> cryptic(String answer, String enumeration, String clue,
                                        String definition, String indicator, String fodder,
                                        String device, String explanation) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", answer);
        m.put("enumeration", enumeration);
        m.put("clue", clue);
        m.put("definition", definition);
        m.put("indicator", indicator);
        m.put("fodder", fodder);
        m.put("device", device);
        m.put("explanation", explanation);
        return m;
    }
}
