package com.eightbit.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {

    @Query("""
            select p from Puzzle p
            where p.gameType = :type and p.publishDate = :date
              and p.status in ('scheduled','published')
            """)
    Optional<Puzzle> findServableForDate(@Param("type") String type, @Param("date") LocalDate date);

    @Query("""
            select p from Puzzle p
            where p.gameType = :type and p.status = 'evergreen'
            """)
    List<Puzzle> findEvergreen(@Param("type") String type);

    /** TRIAL ONLY: the playtest puzzles (synced from puzzles-review.json), oldest day first. */
    @Query("""
            select p from Puzzle p
            where p.gameType = :type and p.status = 'trial'
            order by p.publishDate, p.id
            """)
    List<Puzzle> findTrialPool(@Param("type") String type);

    /** All trial puzzles, for the file→DB sync (upsert + prune). */
    List<Puzzle> findByStatus(String status);

    /**
     * Ids of the old hardcoded demo puzzles seeded by earlier builds. Seed data sets author ==
     * reviewer, which {@code approve()} forbids for real puzzles (SAME_AUTHOR), so this never matches
     * an editor-created puzzle. Scoped to dated 'scheduled' rows; the evergreen failsafe is left alone.
     */
    @Query("""
            select p.id from Puzzle p
            where p.status = 'scheduled' and p.authorId is not null and p.authorId = p.reviewerId
            """)
    List<Long> findSeededDemoIds();

    List<Puzzle> findByGameTypeAndPublishDateBetweenOrderByPublishDate(
            String gameType, LocalDate start, LocalDate end);

    @Query("""
            select count(p) from Puzzle p
            where p.gameType = :type and p.status in ('scheduled','published')
              and p.publishDate >= :from
            """)
    long countScheduledFrom(@Param("type") String type, @Param("from") LocalDate from);

    @Query("""
            select p.publishDate from Puzzle p
            where p.gameType = :type and p.status in ('scheduled','published')
              and p.publishDate >= :from
            order by p.publishDate desc
            """)
    List<LocalDate> lastScheduledDates(@Param("type") String type, @Param("from") LocalDate from, Pageable pageable);
}
