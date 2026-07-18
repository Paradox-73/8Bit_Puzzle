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
}
