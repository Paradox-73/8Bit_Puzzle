package com.eightbit.profile;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.common.security.AuthUser;
import com.eightbit.common.web.ApiException;
import com.eightbit.game.AttemptRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ProfileController {

    private final UserRepository users;
    private final UserStatsRepository statsRepo;
    private final AttemptRepository attempts;

    public ProfileController(UserRepository users, UserStatsRepository statsRepo, AttemptRepository attempts) {
        this.users = users;
        this.statsRepo = statsRepo;
        this.attempts = attempts;
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
                "stats", statsView(s, u.getId(), true)
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
                "stats", statsView(s, u.getId(), false)
        );
    }

    private Map<String, Object> statsView(UserStats s, long userId, boolean includePrivate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("currentStreak", s.getCurrentStreak());
        view.put("bestStreak", s.getBestStreak());
        view.put("totalPlayed", s.getTotalPlayed());
        view.put("totalSolved", s.getTotalSolved());
        view.put("winRate", s.winRatePercent());
        view.put("titles", s.getTitles() == null ? List.of() : s.getTitles());
        // Wordle-only stored distribution kept for back-compat; per-game distributions computed live.
        view.put("guessDistribution", s.getGuessDistribution() == null ? List.of() : s.getGuessDistribution());
        view.put("distributions", distributions(userId));
        if (includePrivate) {
            // Flag state is only for the player themselves, never exposed on public profiles.
            view.put("flagged", s.isFlagged());
            view.put("flagReason", s.getFlagReason() == null ? "" : s.getFlagReason());
        }
        return view;
    }

    /**
     * Per-game guess distributions from finished, solved attempts. Wordle & Cryptic bucket by guesses
     * used (1..6); Connections buckets by mistakes made (0..4 = total selections minus the 4 groups).
     */
    private Map<String, Object> distributions(long userId) {
        int[] wordle = new int[6];
        int[] cryptic = new int[6];
        int[] connections = new int[5];
        for (Object[] r : attempts.solvedMovesByGame(userId)) {
            String game = r[0] == null ? "" : r[0].toString();
            int moves = r[1] == null ? 0 : ((Number) r[1]).intValue();
            int cnt = r[2] == null ? 0 : ((Number) r[2]).intValue();
            switch (game) {
                case "wordle" -> wordle[Math.min(6, Math.max(1, moves)) - 1] += cnt;
                case "cryptic" -> cryptic[Math.min(6, Math.max(1, moves)) - 1] += cnt;
                case "connections" -> connections[Math.min(4, Math.max(0, moves - 4))] += cnt;
                default -> { }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wordle", toList(wordle));
        out.put("cryptic", toList(cryptic));
        out.put("connections", toList(connections));
        return out;
    }

    private List<Integer> toList(int[] arr) {
        List<Integer> out = new ArrayList<>(arr.length);
        for (int v : arr) out.add(v);
        return out;
    }
}
