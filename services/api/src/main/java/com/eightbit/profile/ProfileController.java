package com.eightbit.profile;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.common.security.AuthUser;
import com.eightbit.common.web.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
                        "emailVerified", u.isEmailVerified(),
                        "roles", u.roleList()
                ),
                "stats", statsView(s, true)
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
                "stats", statsView(s, false)
        );
    }

    private Map<String, Object> statsView(UserStats s, boolean includePrivate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("currentStreak", s.getCurrentStreak());
        view.put("bestStreak", s.getBestStreak());
        view.put("totalPlayed", s.getTotalPlayed());
        view.put("totalSolved", s.getTotalSolved());
        view.put("winRate", s.winRatePercent());
        view.put("titles", s.getTitles() == null ? List.of() : s.getTitles());
        view.put("guessDistribution", s.getGuessDistribution() == null ? List.of() : s.getGuessDistribution());
        if (includePrivate) {
            // Flag state is only for the player themselves, never exposed on public profiles.
            view.put("flagged", s.isFlagged());
            view.put("flagReason", s.getFlagReason() == null ? "" : s.getFlagReason());
        }
        return view;
    }
}
