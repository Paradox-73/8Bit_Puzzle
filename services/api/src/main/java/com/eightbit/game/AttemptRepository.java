package com.eightbit.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByUserIdAndPuzzleId(Long userId, Long puzzleId);
    long countByPuzzleIdAndSolvedTrue(Long puzzleId);
    List<Attempt> findTop50ByUserIdOrderByFinishedAtDesc(Long userId);
}
