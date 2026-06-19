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
