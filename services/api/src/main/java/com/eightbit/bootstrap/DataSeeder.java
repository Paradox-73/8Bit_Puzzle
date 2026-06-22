package com.eightbit.bootstrap;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the minimum needed to play immediately on a fresh database: game types, an editor
 * account, 14 days of scheduled Wordle puzzles (so the buffer warning is satisfied), and an
 * evergreen failsafe pool. Idempotent -- skips anything that already exists.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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

    private static final List<String> SCHEDULE_WORDS = List.of(
            "PIXEL", "BYTES", "LOGIC", "CACHE", "ROBOT", "QUEUE", "STACK",
            "DEBUG", "ARRAY", "LINUX", "POWER", "SMART", "BRAIN", "MOUSE");

    private static final List<String> EVERGREEN_WORDS = List.of(
            "MAGIC", "QUICK", "LIGHT", "SOUND", "EARTH", "MUSIC", "DREAM", "PEACE", "HAPPY", "SHINE");

    private final GameTypeRepository gameTypes;
    private final PuzzleRepository puzzles;
    private final UserRepository users;
    private final UserStatsRepository stats;
    private final PasswordEncoder encoder;
    private final WordList wordList;

    public DataSeeder(GameTypeRepository gameTypes, PuzzleRepository puzzles, UserRepository users,
                      UserStatsRepository stats, PasswordEncoder encoder, WordList wordList) {
        this.gameTypes = gameTypes;
        this.puzzles = puzzles;
        this.users = users;
        this.stats = stats;
        this.encoder = encoder;
        this.wordList = wordList;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Seeding disabled (app.seed.enabled=false) -- skipping demo data");
            return;
        }
        seedGameTypes();
        seedEditor();
        seedPuzzles();
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

    private void seedPuzzles() {
        Long editorId = users.findByUsername(EDITOR_USER).map(User::getId).orElse(null);
        LocalDate today = LocalDate.now(IST);

        for (int i = 0; i < SCHEDULE_WORDS.size(); i++) {
            LocalDate date = today.plusDays(i);
            if (puzzles.findServableForDate("wordle", date).isPresent()) continue;
            String word = SCHEDULE_WORDS.get(i).toUpperCase();
            wordList.register(word);
            Puzzle p = new Puzzle();
            p.setGameType("wordle");
            p.setPublishDate(date);
            p.setDifficulty(difficultyFor(date));
            p.setContent(answer(word));
            if (i == 0) {
                // Demo easter egg: typing BYTES while solving today's Wordle pops a club message.
                Map<String, Object> eggs = new HashMap<>();
                eggs.put("triggers", List.of(Map.of(
                        "match", "BYTES",
                        "title", "🥚 8-bit secret",
                        "body", "You typed the magic word. The 8Bit club salutes you!")));
                p.setEasterEggs(eggs);
            }
            p.setStatus(PuzzleStatus.SCHEDULED);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId); // seed data only; real puzzles need a second reviewer
            try {
                puzzles.save(p);
            } catch (Exception dup) {
                // unique (game_type, publish_date) -> already there, ignore
            }
        }

        seedConnections(editorId, today);
        seedCryptic(editorId, today);

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
        log.info("Seeded {} days of scheduled Wordle + evergreen failsafe pool", SCHEDULE_WORDS.size());
    }

    private Map<String, Object> answer(String word) {
        Map<String, Object> m = new HashMap<>();
        m.put("answer", word);
        return m;
    }

    /** Two campus-themed Connections sets, alternated across the next week. */
    private void seedConnections(Long editorId, LocalDate today) {
        List<List<Map<String, Object>>> variants = List.of(
                List.of(
                        group(0, "Hostel life", "WIFI", "MESS", "LAUNDRY", "CURFEW"),
                        group(1, "Things in code", "PYTHON", "JAVA", "DEBUG", "COMMIT"),
                        group(2, "Spots on campus", "ATRIUM", "LIBRARY", "COURT", "GAZEBO"),
                        group(3, "___ test", "UNIT", "STRESS", "BETA", "ACID")),
                List.of(
                        group(0, "Endsem energy", "PANIC", "CRAM", "REDBULL", "ALLNIGHT"),
                        group(1, "8-bit things", "PIXEL", "SPRITE", "BYTE", "RETRO"),
                        group(2, "Mess menu", "POHA", "DOSA", "RICE", "CHAI"),
                        group(3, "Network ___", "STACK", "PACKET", "SOCKET", "ROUTER")));

        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            if (puzzles.findServableForDate("connections", date).isPresent()) continue;
            Map<String, Object> content = new HashMap<>();
            content.put("groups", variants.get(i % variants.size()));
            Puzzle p = new Puzzle();
            p.setGameType("connections");
            p.setPublishDate(date);
            p.setDifficulty(difficultyFor(date));
            p.setContent(content);
            p.setStatus(PuzzleStatus.SCHEDULED);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId);
            try {
                puzzles.save(p);
            } catch (Exception dup) {
                // already there
            }
        }
        log.info("Seeded 7 days of Connections puzzles");
    }

    private Map<String, Object> group(int level, String category, String... members) {
        Map<String, Object> g = new HashMap<>();
        g.put("level", level);
        g.put("category", category);
        g.put("members", List.of(members));
        return g;
    }

    /**
     * Beginner-friendly cryptic clues, one per day, rotating through the main devices (anagram,
     * hidden, reversal, charade, container, deletion, homophone, double-definition). Each carries the
     * three teaching hints (definition / indicator / fodder) plus the full parse shown on solve.
     */
    private void seedCryptic(Long editorId, LocalDate today) {
        List<Map<String, Object>> clues = List.of(
                cryptic("LISTEN", "(6)", "Pay attention to broken tinsel (6)",
                        "Pay attention", "broken", "tinsel", "Anagram",
                        "LISTEN ('pay attention') is an anagram (‘broken’) of TINSEL."),
                cryptic("HEART", "(5)", "Some of the artistic core (5)",
                        "core", "Some of", "the artistic", "Hidden word",
                        "HEART is hidden inside 'tHE ARTistic'."),
                cryptic("STRAW", "(5)", "Drinking tube made from warts sent back (5)",
                        "Drinking tube", "sent back", "warts", "Reversal",
                        "WARTS reversed ('sent back') spells STRAW."),
                cryptic("CARPET", "(6)", "Vehicle's animal companion covers the floor (6)",
                        "covers the floor", "build from parts", "CAR (vehicle) + PET (animal companion)",
                        "Charade", "CAR (vehicle) + PET (animal companion) = CARPET."),
                cryptic("STABLE", "(6)", "Steady, capable, framed by street (6)",
                        "Steady", "framed by", "ABLE (capable) inside ST (street)", "Container",
                        "ABLE ('capable') placed inside ST (abbrev. for street) = STABLE."),
                cryptic("COLD", "(4)", "Chilly scold's head dropped (4)",
                        "Chilly", "head dropped", "scold", "Deletion",
                        "SCOLD with its head (first letter) dropped = COLD."),
                cryptic("NIGHT", "(5)", "Reportedly a medieval warrior brings darkness (5)",
                        "darkness", "Reportedly", "knight (medieval warrior)", "Homophone",
                        "NIGHT sounds like KNIGHT ('medieval warrior') — 'reportedly' flags the homophone."),
                cryptic("LEFT", "(4)", "Departed, but remaining (4)",
                        "Departed / remaining", "two meanings", "—", "Double definition",
                        "LEFT means both 'departed' and 'what remains' — two definitions in one."));

        for (int i = 0; i < clues.size(); i++) {
            LocalDate date = today.plusDays(i);
            if (puzzles.findServableForDate("cryptic", date).isPresent()) continue;
            Puzzle p = new Puzzle();
            p.setGameType("cryptic");
            p.setPublishDate(date);
            p.setDifficulty(difficultyFor(date));
            p.setContent(clues.get(i));
            p.setStatus(PuzzleStatus.SCHEDULED);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId);
            try {
                puzzles.save(p);
            } catch (Exception dup) {
                // unique (game_type, publish_date) -> already there, ignore
            }
        }

        // Evergreen failsafe so there's always a cryptic to serve.
        if (puzzles.findEvergreen("cryptic").isEmpty()) {
            Puzzle p = new Puzzle();
            p.setGameType("cryptic");
            p.setPublishDate(null);
            p.setDifficulty((short) 2);
            p.setContent(clues.get(0));
            p.setStatus(PuzzleStatus.EVERGREEN);
            p.setAuthorId(editorId);
            p.setReviewerId(editorId);
            puzzles.save(p);
        }
        log.info("Seeded {} days of cryptic clues + evergreen failsafe", clues.size());
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

    /** Easy on Monday, hardest on Friday/weekend (build doc section 2). */
    private short difficultyFor(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 2;
            case THURSDAY -> 3;
            case FRIDAY -> 4;
            case SATURDAY -> 5;
            case SUNDAY -> 3;
        };
    }
}
