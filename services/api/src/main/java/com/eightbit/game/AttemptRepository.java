package com.eightbit.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByUserIdAndPuzzleId(Long userId, Long puzzleId);
    long countByPuzzleIdAndSolvedTrue(Long puzzleId);
    List<Attempt> findTop50ByUserIdOrderByFinishedAtDesc(Long userId);
    List<Attempt> findTop100ByFlaggedTrueOrderByFinishedAtDesc();

    /** Delete every attempt on the given puzzles. Needed before deleting puzzles (FK puzzle_id). */
    @Modifying
    @Transactional
    @Query("delete from Attempt a where a.puzzleId in :puzzleIds")
    int deleteByPuzzleIds(@Param("puzzleIds") List<Long> puzzleIds);

    // ----- TRIAL ONLY (pre-launch playtest; removable with trial mode) -----

    /** Puzzle ids this user has already FINISHED — used to serve the next unplayed trial puzzle. */
    @Query("select a.puzzleId from Attempt a where a.userId = :userId and a.finishedAt is not null")
    List<Long> finishedPuzzleIds(@Param("userId") Long userId);

    /** Per-(game type, difficulty) aggregates: attempts, solves, avg time(ms), avg guesses.
     *  Pass trial=true for playtest data, trial=false for real (junior) play — kept separate. */
    @Query(value = """
            SELECT p.game_type AS gameType, p.difficulty AS difficulty,
                   COUNT(*) AS attempts,
                   SUM(CASE WHEN a.solved THEN 1 ELSE 0 END) AS solves,
                   AVG(a.completion_ms) AS avgMs,
                   AVG(jsonb_array_length(a.guesses)) AS avgGuesses
            FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
            WHERE a.trial = :trial AND a.finished_at IS NOT NULL
            GROUP BY p.game_type, p.difficulty
            ORDER BY p.game_type, p.difficulty
            """, nativeQuery = true)
    List<Object[]> statsByDifficulty(@Param("trial") boolean trial);

    /** Per-player summary: distinct puzzles finished, solves, total time, last activity. */
    @Query(value = """
            SELECT a.user_id AS userId,
                   COUNT(*) AS attempts,
                   SUM(CASE WHEN a.solved THEN 1 ELSE 0 END) AS solves,
                   SUM(a.completion_ms) AS totalMs,
                   MAX(a.finished_at) AS lastAt
            FROM attempts a
            WHERE a.trial = :trial AND a.finished_at IS NOT NULL
            GROUP BY a.user_id
            ORDER BY solves DESC
            """, nativeQuery = true)
    List<Object[]> statsByUser(@Param("trial") boolean trial);

    /** Per-(game type, difficulty) average star rating from trial playtesters. */
    @Query(value = """
            SELECT p.game_type AS gameType, p.difficulty AS difficulty,
                   AVG(a.rating) AS avgRating, COUNT(a.rating) AS ratings
            FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
            WHERE a.trial = true AND a.rating IS NOT NULL
            GROUP BY p.game_type, p.difficulty
            """, nativeQuery = true)
    List<Object[]> trialRatingsByDifficulty();

    /** Trial "what to change" notes, newest first, with the puzzle they're about. */
    @Query(value = """
            SELECT a.user_id AS userId, p.game_type AS gameType, p.publish_date AS date,
                   p.difficulty AS difficulty, a.rating AS rating, a.feedback AS feedback,
                   a.finished_at AS at
            FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
            WHERE a.trial = true AND a.feedback IS NOT NULL AND a.feedback <> ''
            ORDER BY a.id DESC
            LIMIT 300
            """, nativeQuery = true)
    List<Object[]> trialComments();

    /** Purge all trial attempts. Run before go-live to leave a clean slate. Returns rows deleted. */
    @Modifying
    @Transactional
    @Query("delete from Attempt a where a.trial = true")
    int deleteAllTrial();

    /** Distinct solvers of today's puzzle(s) grouped by (program, batch year), for the Batch War.
     *  type "all" counts anyone who solved any game today; otherwise just that game. */
    @Query(value = """
            SELECT u.program AS program, u.batch_year AS year, COUNT(DISTINCT a.user_id) AS solvers
            FROM attempts a
            JOIN users u ON u.id = a.user_id
            JOIN puzzles p ON p.id = a.puzzle_id
            WHERE p.publish_date = :date AND a.solved = true
              AND (:type = 'all' OR p.game_type = :type)
            GROUP BY u.program, u.batch_year
            """, nativeQuery = true)
    List<Object[]> solversByCohort(@Param("date") LocalDate date, @Param("type") String type);
}
