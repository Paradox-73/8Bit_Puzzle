package com.eightbit.game;

import com.eightbit.auth.UserRepository;
import com.eightbit.auth.User;
import com.eightbit.auth.otp.EmailSender;
import com.eightbit.common.config.AppProperties;
import com.eightbit.common.ratelimit.RateLimiter;
import com.eightbit.common.web.ApiException;
import com.eightbit.game.dto.GameDtos.AttemptSummary;
import com.eightbit.game.event.PuzzleCompletedEvent;
import com.eightbit.game.play.GamePlay;
import com.eightbit.profile.StatsService;
import com.eightbit.profile.UserStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GameService {

    /** The puzzle "day" rolls over at midnight IST for everyone. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private static final int GUESS_LIMIT = 30;
    private static final Duration GUESS_WINDOW = Duration.ofSeconds(10);

    private final PuzzleRepository puzzles;
    private final AttemptRepository attempts;
    private final Map<String, GamePlay> plays = new LinkedHashMap<>();
    private final StatsService statsService;
    private final EasterEggService easterEggs;
    private final RateLimiter rateLimiter;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final EmailSender email;
    private final String teamEmail;
    private final AppProperties appProps;
    private final TrialPuzzleSync trialSync;

    public GameService(PuzzleRepository puzzles, AttemptRepository attempts, List<GamePlay> playList,
                       StatsService statsService, EasterEggService easterEggs, RateLimiter rateLimiter,
                       UserRepository users, ApplicationEventPublisher events, EmailSender email,
                       AppProperties appProps, TrialPuzzleSync trialSync,
                       @Value("${app.feedback.to:8bit@iiitb.ac.in}") String teamEmail) {
        this.puzzles = puzzles;
        this.attempts = attempts;
        playList.forEach(p -> plays.put(p.type(), p));
        this.statsService = statsService;
        this.easterEggs = easterEggs;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.events = events;
        this.email = email;
        this.appProps = appProps;
        this.trialSync = trialSync;
        this.teamEmail = teamEmail;
    }

    /**
     * Re-import puzzles-review.json if it changed. Called (outside any read-only transaction) before
     * serving/playing in trial mode, so edits to the file are live. No-op when trial is inactive.
     */
    public void maybeSyncTrial() {
        if (trialActive()) trialSync.syncIfChanged();
    }

    // ===== TRIAL MODE (pre-launch playtest; entirely flag-gated, removable) =====
    // When active, players walk every published puzzle back-to-back with no daily gate, and every
    // completion is tagged trial=true and kept out of streaks/scoring-by-streak/leaderboard.

    /** True only while the trial flag is on AND we're on/before the configured end date. */
    public boolean trialActive() {
        AppProperties.Trial t = appProps.getTrial();
        return t.isEnabled() && !today().isAfter(t.getEndDate());
    }

    private boolean isTrialPlayable(Puzzle p) {
        String s = p.getStatus();
        return PuzzleStatus.SCHEDULED.equals(s) || PuzzleStatus.PUBLISHED.equals(s)
                || PuzzleStatus.EVERGREEN.equals(s);
    }

    /** Guard for guess/hint: in trial any published puzzle is playable; otherwise only today's. */
    private void assertPlayable(Puzzle p) {
        if (trialActive() && isTrialPlayable(p)) return;
        Puzzle todayPuzzle = resolveTodayPuzzle(p.getGameType());
        if (!todayPuzzle.getId().equals(p.getId())) {
            throw ApiException.forbidden("NOT_TODAY", "That puzzle is not in play right now");
        }
    }

    /** The trial view: the next puzzle the user hasn't finished yet, plus walk-through progress. */
    @Transactional(readOnly = true)
    public Map<String, Object> trialToday(long userId, String type) {
        List<Puzzle> pool = puzzles.findTrialPool(type);
        Set<Long> finished = new HashSet<>(attempts.finishedPuzzleIds(userId));
        int total = pool.size();
        int done = (int) pool.stream().filter(p -> finished.contains(p.getId())).count();

        Puzzle p = pool.stream().filter(pz -> !finished.contains(pz.getId())).findFirst().orElse(null);
        if (p == null) {
            Map<String, Object> doneView = new LinkedHashMap<>();
            doneView.put("gameType", type);
            doneView.put("trial", true);
            doneView.put("trialDone", true);
            doneView.put("trialTotal", total);
            doneView.put("trialIndex", done);
            return doneView;
        }

        GamePlay gp = play(type);
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, p.getId()).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("puzzleId", p.getId());
        view.put("gameType", type);
        view.put("date", p.getPublishDate() == null ? null : p.getPublishDate().toString());
        view.put("difficulty", p.getDifficulty());
        view.put("trial", true);
        view.put("trialTotal", total);
        view.put("trialIndex", done + 1); // 1-based position in the walk
        addCampusFlag(view, p); // front-end badge for non-dictionary / IIITB answers
        // Surface any rating/note this player already left, so the widget can show their last answer.
        if (attempt != null) {
            if (attempt.getRating() != null) view.put("myRating", attempt.getRating());
            if (attempt.getFeedback() != null) view.put("myFeedback", attempt.getFeedback());
        }
        view.putAll(gp.todayView(p, attempt));
        view.put("status", status(attempt));
        if (attempt != null && attempt.isFinished()) {
            view.put("score", attempt.getScore());
            view.putAll(gp.reveal(p, attempt));
        } else {
            view.put("score", null);
        }
        return view;
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private GamePlay play(String type) {
        GamePlay p = plays.get(type);
        if (p == null) throw ApiException.badRequest("UNKNOWN_GAME", "Unknown game type: " + type);
        return p;
    }

    /** Scheduled puzzle for today, else a deterministic pick from the evergreen failsafe pool. */
    @Transactional(readOnly = true)
    public Puzzle resolveTodayPuzzle(String type) {
        LocalDate date = today();
        return puzzles.findServableForDate(type, date).orElseGet(() -> {
            List<Puzzle> pool = puzzles.findEvergreen(type);
            if (pool.isEmpty()) {
                throw ApiException.notFound("NO_PUZZLE", "No puzzle available for today");
            }
            int idx = (int) Math.floorMod(date.toEpochDay(), pool.size());
            return pool.get(idx);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> today(long userId, String type) {
        if (trialActive()) {
            return trialToday(userId, type);
        }
        Puzzle p = resolveTodayPuzzle(type);
        GamePlay gp = play(type);
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, p.getId()).orElse(null);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("puzzleId", p.getId());
        view.put("gameType", type);
        view.put("date", today().toString());
        view.put("difficulty", p.getDifficulty());
        addCampusFlag(view, p);
        view.putAll(gp.todayView(p, attempt));
        view.put("status", status(attempt));
        if (attempt != null && attempt.isFinished()) {
            view.put("score", attempt.getScore());
            view.putAll(gp.reveal(p, attempt));
        } else {
            view.put("score", null);
        }
        return view;
    }

    /** Flag a puzzle whose answer isn't a normal dictionary word (IIITB term / brand), so the
     *  front-end can show a small "campus word" badge — only ever set for wordle/cryptic content. */
    private void addCampusFlag(Map<String, Object> view, Puzzle p) {
        if (p.getContent() != null && Boolean.TRUE.equals(p.getContent().get("campusWord"))) {
            view.put("campusWord", true);
        }
    }

    /** Trial only: save a playtester's 1–5 rating and/or "what to change" note for a puzzle. */
    @Transactional
    public Map<String, Object> rate(long userId, long puzzleId, Integer rating, String message) {
        Puzzle p = puzzles.findById(puzzleId)
                .orElseThrow(() -> ApiException.notFound("NO_PUZZLE", "Puzzle not found"));
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, p.getId())
                .orElseGet(() -> new Attempt(userId, p.getId()));
        if (trialActive()) attempt.setTrial(true);
        if (rating != null) attempt.setRating((short) Math.max(1, Math.min(5, rating)));
        if (message != null) {
            String m = message.strip();
            attempt.setFeedback(m.length() > 1000 ? m.substring(0, 1000) : m);
        }
        attempts.save(attempt);
        return Map.of("ok", true);
    }

    /** Reveal one hint (vowel/consonant) for today's in-progress puzzle. */
    @Transactional
    public Map<String, Object> hint(long userId, long puzzleId, String kind) {
        Puzzle p = puzzles.findById(puzzleId)
                .orElseThrow(() -> ApiException.notFound("NO_PUZZLE", "Puzzle not found"));
        assertPlayable(p);
        if (!rateLimiter.allow("hint:" + userId, GUESS_LIMIT, GUESS_WINDOW)) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Slow down — too many requests");
        }
        GamePlay gp = play(p.getGameType());
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, puzzleId)
                .orElseGet(() -> new Attempt(userId, puzzleId));
        if (trialActive()) attempt.setTrial(true);
        if (attempt.isFinished()) {
            throw ApiException.conflict("ALREADY_FINISHED", "You've already finished today's puzzle");
        }
        Map<String, Object> hint = gp.hint(p, attempt, kind);
        attempts.save(attempt);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("hint", hint);
        resp.put("hints", attempt.getHints());
        resp.put("status", status(attempt));
        return resp;
    }

    @Transactional
    public Map<String, Object> guess(long userId, String username, int batchYear, long puzzleId,
                                     Map<String, Object> move) {
        Puzzle p = puzzles.findById(puzzleId)
                .orElseThrow(() -> ApiException.notFound("NO_PUZZLE", "Puzzle not found"));

        // Anti-cheat: you may only play today's resolved puzzle (relaxed to the whole pool in trial).
        boolean trial = trialActive();
        assertPlayable(p);

        GamePlay gp = play(p.getGameType());
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, puzzleId)
                .orElseGet(() -> new Attempt(userId, puzzleId));
        if (trial) attempt.setTrial(true);
        if (attempt.isFinished()) {
            throw ApiException.conflict("ALREADY_FINISHED", "You've already finished today's puzzle");
        }

        // Rate limit guess submissions to stop scripts.
        if (!rateLimiter.allow("guess:" + userId, GUESS_LIMIT, GUESS_WINDOW)) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Slow down — too many requests");
        }

        GamePlay.MoveStep st = gp.step(p, attempt, move);

        Map<String, Object> resp = new LinkedHashMap<>(st.response());
        resp.put("solved", st.solved());
        resp.put("gameOver", st.gameOver());

        if (st.gameOver()) {
            long ms = Math.max(0, Duration.between(attempt.getStartedAt(), Instant.now()).toMillis());
            int completionMs = (int) Math.min(Integer.MAX_VALUE, ms);
            int guessesUsed = attempt.getGuesses().size();

            attempt.setSolved(st.solved());
            attempt.setCompletionMs(completionMs);
            attempt.setFinishedAt(Instant.now());

            if (trial) {
                // Playtest: record timing/guesses for stats, but stay out of streaks, the
                // leaderboard and the quick-solve alerting. Score (no streak) is informational only.
                attempt.setScore(gp.score(p, attempt, 0));
                applyAuditFlags(attempt, st.solved(), completionMs);
            } else {
                boolean firstSolver = st.solved() && attempts.countByPuzzleIdAndSolvedTrue(puzzleId) == 0;
                int solveHour = ZonedDateTime.now(ZONE).getHour();
                UserStats stats = statsService.recordCompletion(userId, today(), st.solved(), firstSolver,
                        solveHour, guessesUsed, p.getGameType());
                attempt.setScore(gp.score(p, attempt, stats.getCurrentStreak()));
                applyAuditFlags(attempt, st.solved(), completionMs);
                maybeWarnQuickSolve(resp, attempt, stats, st.solved(), guessesUsed, p.getGameType(),
                        userId, username, batchYear);
            }

            resp.put("score", attempt.getScore());
            resp.put("shareGrid", gp.shareGrid(p, attempt));
            resp.putAll(gp.reveal(p, attempt));
        }

        resp.put("status", status(attempt));

        Map<String, Object> egg = easterEggs.eval(p, move, st.solved());
        if (egg != null) resp.put("easterEgg", egg);

        attempts.save(attempt);

        if (st.gameOver() && !trial) {
            // Unverified accounts still appear on the campus board but don't count toward batch
            // scores -- the anti-batch-stuffing rule (build doc section 6). Verified-by-default
            // when OTP is off.
            boolean verified = users.findById(userId).map(User::isEmailVerified).orElse(true);
            events.publishEvent(new PuzzleCompletedEvent(
                    userId, username, batchYear, p.getGameType(), p.getId(), today(),
                    attempt.getScore(), st.solved(), verified));
        }
        return resp;
    }

    /** Flag finished attempts that look automated (build doc section 14). */
    private void applyAuditFlags(Attempt a, boolean solved, int completionMs) {
        if (!solved) return;
        int moves = a.getGuesses().size();
        if (completionMs < 1500 || (moves >= 3 && completionMs < 4000)) {
            a.setFlagged(true);
            a.setFlagReason("Solved in " + completionMs + "ms over " + moves + " moves");
        }
    }

    /**
     * When a player keeps solving in ≤2 guesses on consecutive days (a leaked-answer tell), warn
     * them in the response, flag the attempt, and email the team. Wordle only.
     */
    private void maybeWarnQuickSolve(Map<String, Object> resp, Attempt attempt, UserStats stats,
                                     boolean solved, int guessesUsed, String gameType,
                                     long userId, String username, int batchYear) {
        boolean tripped = "wordle".equals(gameType) && solved
                && guessesUsed <= 2 && stats.isFlagged();
        if (!tripped) return;

        attempt.setFlagged(true);
        attempt.setFlagReason(stats.getFlagReason());
        // Player-facing: a gentle nudge only. The detection detail stays in the team email below.
        resp.put("warning", "A friendly reminder to play fair and solve the puzzles yourself 🙂");

        try {
            email.send(teamEmail, "[8Bit] Quick-solve flag: " + username,
                    "User '" + username + "' (id=" + userId + ", batch=" + batchYear + ") was flagged.\n"
                    + "Reason: " + stats.getFlagReason() + "\n"
                    + "Today's attempt: solved in " + guessesUsed + " guesses, puzzle="
                    + attempt.getPuzzleId() + ".");
        } catch (Exception e) {
            log.warn("Failed to send quick-solve flag email for user {}: {}", userId, e.getMessage());
        }
    }

    private String status(Attempt a) {
        if (a == null || a.getGuesses().isEmpty()) return "NOT_STARTED";
        if (!a.isFinished()) return "IN_PROGRESS";
        return Boolean.TRUE.equals(a.getSolved()) ? "SOLVED" : "FAILED";
    }

    @Transactional(readOnly = true)
    public List<AttemptSummary> myAttempts(long userId) {
        List<AttemptSummary> out = new ArrayList<>();
        for (Attempt a : attempts.findTop50ByUserIdOrderByFinishedAtDesc(userId)) {
            Puzzle p = puzzles.findById(a.getPuzzleId()).orElse(null);
            out.add(new AttemptSummary(
                    a.getPuzzleId(),
                    p == null ? null : p.getGameType(),
                    p == null ? null : p.getPublishDate(),
                    a.getSolved(), a.getScore(), a.getGuesses().size()));
        }
        return out;
    }
}
