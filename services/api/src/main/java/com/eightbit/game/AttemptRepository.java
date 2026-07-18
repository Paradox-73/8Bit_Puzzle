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

    /** Per-(game type, #moves) counts for ONE user's solved attempts — builds the profile distributions. */
    @Query(value = """
            SELECT p.game_type AS gameType, jsonb_array_length(a.guesses) AS moves, COUNT(*) AS cnt
            FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
            WHERE a.user_id = :userId AND a.solved = true AND a.finished_at IS NOT NULL
            GROUP BY p.game_type, jsonb_array_length(a.guesses)
            """, nativeQuery = true)
    List<Object[]> solvedMovesByGame(@Param("userId") long userId);

    /** Admin dashboard: per game type across ALL players — finished, solves, avg time, avg moves, players. */
    @Query(value = """
            SELECT p.game_type AS gameType, COUNT(*) AS attempts,
                   SUM(CASE WHEN a.solved THEN 1 ELSE 0 END) AS solves,
                   AVG(a.completion_ms) AS avgMs, AVG(jsonb_array_length(a.guesses)) AS avgMoves,
                   COUNT(DISTINCT a.user_id) AS players
            FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
            WHERE a.finished_at IS NOT NULL
            GROUP BY p.game_type
            ORDER BY p.game_type
            """, nativeQuery = true)
    List<Object[]> liveStatsByGame();

    /** Admin dashboard: top players by total solves across all games. */
    @Query(value = """
            SELECT a.user_id AS userId, COUNT(*) AS attempts,
                   SUM(CASE WHEN a.solved THEN 1 ELSE 0 END) AS solves,
                   MAX(a.finished_at) AS lastAt
            FROM attempts a
            WHERE a.finished_at IS NOT NULL
            GROUP BY a.user_id
            ORDER BY solves DESC, attempts DESC
            LIMIT 100
            """, nativeQuery = true)
    List<Object[]> liveTopPlayers();
}
