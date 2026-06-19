package com.eightbit.profile;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.common.security.AuthUser;
import com.eightbit.common.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProfileController {

    private final UserRepository users;
    private final UserStatsRepository statsRepo;

    public ProfileController(UserRepository users, UserStatsRepository statsRepo) {
        this.users = users;
        this.statsRepo = statsRepo;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser principal) {
        User u = users.findById(principal.id())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        UserStats s = statsRepo.findById(u.getId()).orElseGet(() -> new UserStats(u.getId()));
        return Map.of(
                "user", Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "rollNumber", u.getRollNumber(),
                        "batchYear", u.getBatchYear(),
                        "program", u.getProgram() == null ? "" : u.getProgram(),
                        "roles", u.roleList()
                ),
                "stats", statsView(s)
        );
    }

    @GetMapping("/users/{username}")
    public Map<String, Object> publicProfile(@PathVariable String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No such player"));
        UserStats s = statsRepo.findById(u.getId()).orElseGet(() -> new UserStats(u.getId()));
        return Map.of(
                "username", u.getUsername(),
                "batchYear", u.getBatchYear(),
                "program", u.getProgram() == null ? "" : u.getProgram(),
                "stats", statsView(s)
        );
    }

    private Map<String, Object> statsView(UserStats s) {
        return Map.of(
                "currentStreak", s.getCurrentStreak(),
                "bestStreak", s.getBestStreak(),
                "totalPlayed", s.getTotalPlayed(),
                "totalSolved", s.getTotalSolved(),
                "winRate", s.winRatePercent(),
                "titles", s.getTitles() == null ? List.of() : s.getTitles()
        );
    }
}
