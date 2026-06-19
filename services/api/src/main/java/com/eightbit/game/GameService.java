package com.eightbit.game;

import com.eightbit.common.web.ApiException;
import com.eightbit.game.dto.GameDtos.*;
import com.eightbit.game.event.PuzzleCompletedEvent;
import com.eightbit.game.wordle.WordList;
import com.eightbit.game.wordle.WordleEngine;
import com.eightbit.profile.StatsService;
import com.eightbit.profile.UserStats;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GameService {

    /** The puzzle "day" rolls over at midnight IST for everyone. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final PuzzleRepository puzzles;
    private final AttemptRepository attempts;
    private final WordleEngine engine;
    private final WordList wordList;
    private final StatsService statsService;
    private final ApplicationEventPublisher events;

    public GameService(PuzzleRepository puzzles, AttemptRepository attempts, WordleEngine engine,
                       WordList wordList, StatsService statsService, ApplicationEventPublisher events) {
        this.puzzles = puzzles;
        this.attempts = attempts;
        this.engine = engine;
        this.wordList = wordList;
        this.statsService = statsService;
        this.events = events;
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Resolves the puzzle to serve for {@code type} today: the scheduled one if present, otherwise
     * a deterministic pick from the evergreen failsafe pool so the game is NEVER empty.
     */
    @Transactional(readOnly = true)
    public Puzzle resolveTodayPuzzle(String type) {
        LocalDate date = today();
        return puzzles.findServableForDate(type, date).orElseGet(() -> {
            List<Puzzle> pool = puzzles.findEvergreen(type);
            if (pool.isEmpty()) {
                throw ApiException.notFound("NO_PUZZLE", "No puzzle available for today");
            }
            // Stable for the whole day and identical for every player -> fair leaderboard.
            int idx = (int) Math.floorMod(date.toEpochDay(), pool.size());
            return pool.get(idx);
        });
    }

    @Transactional(readOnly = true)
    public TodayResponse today(long userId, String type) {
        Puzzle p = resolveTodayPuzzle(type);
        LocalDate playDate = today();
        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, p.getId()).orElse(null);

        String answer = p.answer();
        List<GuessRow> rows = new ArrayList<>();
        String status = "NOT_STARTED";
        Integer score = null;
        String revealed = null;

        if (attempt != null) {
            for (String g : attempt.getGuesses()) {
                rows.add(new GuessRow(g, engine.score(g, answer)));
            }
            if (attempt.isFinished()) {
                status = Boolean.TRUE.equals(attempt.getSolved()) ? "SOLVED" : "FAILED";
                score = attempt.getScore();
                revealed = answer; // round is over -> safe to reveal
            } else {
                status = "IN_PROGRESS";
            }
        }

        return new TodayResponse(
                p.getId(), type, playDate, p.getDifficulty(),
                new Config(WordleEngine.MAX_GUESSES, WordleEngine.WORD_LENGTH),
                status, rows, score, revealed);
    }

    @Transactional
    public GuessResponse guess(long userId, String username, int batchYear, long puzzleId, String rawGuess) {
        Puzzle p = puzzles.findById(puzzleId)
                .orElseThrow(() -> ApiException.notFound("NO_PUZZLE", "Puzzle not found"));

        // Anti-cheat: you may only guess on today's resolved puzzle, not arbitrary past/future ones.
        Puzzle todayPuzzle = resolveTodayPuzzle(p.getGameType());
        if (!todayPuzzle.getId().equals(p.getId())) {
            throw ApiException.forbidden("NOT_TODAY", "That puzzle is not in play right now");
        }

        Attempt attempt = attempts.findByUserIdAndPuzzleId(userId, puzzleId)
                .orElseGet(() -> new Attempt(userId, puzzleId));
        if (attempt.isFinished()) {
            throw ApiException.conflict("ALREADY_FINISHED", "You've already finished today's puzzle");
        }

        String guess = rawGuess == null ? "" : rawGuess.trim().toUpperCase();
        if (guess.length() != WordleEngine.WORD_LENGTH) {
            throw ApiException.badRequest("BAD_LENGTH", "Guess must be 5 letters");
        }
        if (!wordList.isAllowed(guess)) {
            throw ApiException.badRequest("NOT_IN_WORD_LIST", "Not in word list");
        }

        String answer = p.answer();
        List<String> pattern = engine.score(guess, answer);
        attempt.getGuesses().add(guess);
        int guessesUsed = attempt.getGuesses().size();
        boolean solved = engine.isSolved(pattern);
        boolean gameOver = solved || guessesUsed >= WordleEngine.MAX_GUESSES;

        Integer score = null;
        String revealed = null;
        String shareGrid = null;

        if (gameOver) {
            long ms = Math.max(0, Duration.between(attempt.getStartedAt(), Instant.now()).toMillis());
            int completionMs = (int) Math.min(Integer.MAX_VALUE, ms);
            boolean firstSolver = solved && attempts.countByPuzzleIdAndSolvedTrue(puzzleId) == 0;
            int solveHour = ZonedDateTime.now(ZONE).getHour();

            UserStats stats = statsService.recordCompletion(userId, today(), solved, firstSolver, solveHour);
            int computed = engine.score(solved, guessesUsed, completionMs, stats.getCurrentStreak());

            attempt.setSolved(solved);
            attempt.setScore(computed);
            attempt.setCompletionMs(completionMs);
            attempt.setFinishedAt(Instant.now());
            score = computed;
            revealed = answer;
            shareGrid = buildShareGrid(attempt, answer, solved);
        }

        attempts.save(attempt);

        if (gameOver) {
            // Delivered AFTER_COMMIT to the leaderboard listener -> the player never waits on ranking.
            events.publishEvent(new PuzzleCompletedEvent(
                    userId, username, batchYear, p.getGameType(), p.getId(), today(), score, solved));
        }

        String status = !gameOver ? "IN_PROGRESS" : (solved ? "SOLVED" : "FAILED");
        return new GuessResponse(guessesUsed, pattern, solved, gameOver, guessesUsed,
                WordleEngine.MAX_GUESSES, status, score, revealed, shareGrid);
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

    private String buildShareGrid(Attempt attempt, String answer, boolean solved) {
        StringBuilder sb = new StringBuilder();
        String n = solved ? String.valueOf(attempt.getGuesses().size()) : "X";
        sb.append("8Bit • IIITB • ").append(n).append("/").append(WordleEngine.MAX_GUESSES).append("\n");
        for (String g : attempt.getGuesses()) {
            for (String cell : engine.score(g, answer)) {
                sb.append(switch (cell) {
                    case WordleEngine.GREEN -> "🟩";  // 🟩
                    case WordleEngine.YELLOW -> "🟨"; // 🟨
                    default -> "⬛";                        // ⬛
                });
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
