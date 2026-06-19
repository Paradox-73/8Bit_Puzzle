package com.eightbit.admin.dto;

import java.time.LocalDate;
import java.util.Map;

public class AdminDtos {

    public record CreatePuzzleRequest(
            String gameType,
            LocalDate publishDate,      // null allowed -> evergreen candidate
            Short difficulty,
            Map<String, Object> content,
            Map<String, Object> easterEggs
    ) {}

    public record UpdatePuzzleRequest(
            LocalDate publishDate,
            Short difficulty,
            Map<String, Object> content,
            Map<String, Object> easterEggs
    ) {}

    public record ScheduleRequest(LocalDate publishDate) {}

    public record PuzzleView(
            Long id,
            String gameType,
            LocalDate publishDate,
            Short difficulty,
            String status,
            Map<String, Object> content,
            Map<String, Object> easterEggs,
            Long authorId,
            Long reviewerId
    ) {}
}
