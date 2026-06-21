package com.eightbit.admin;

import com.eightbit.auth.User;
import com.eightbit.auth.UserRepository;
import com.eightbit.game.Attempt;
import com.eightbit.game.AttemptRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets editors review attempts flagged by the anti-cheat heuristics before any prize goes out
 * (build doc section 14). Gated to ROLE_EDITOR/ROLE_ADMIN by SecurityConfig (/admin/**).
 */
@RestController
@RequestMapping("/admin")
public class AdminAuditController {

    private final AttemptRepository attempts;
    private final UserRepository users;

    public AdminAuditController(AttemptRepository attempts, UserRepository users) {
        this.attempts = attempts;
        this.users = users;
    }

    @GetMapping("/flagged")
    public List<Map<String, Object>> flagged() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Attempt a : attempts.findTop100ByFlaggedTrueOrderByFinishedAtDesc()) {
            User u = users.findById(a.getUserId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attemptId", a.getId());
            m.put("userId", a.getUserId());
            m.put("username", u == null ? null : u.getUsername());
            m.put("rollNumber", u == null ? null : u.getRollNumber());
            m.put("puzzleId", a.getPuzzleId());
            m.put("score", a.getScore());
            m.put("completionMs", a.getCompletionMs());
            m.put("reason", a.getFlagReason());
            m.put("finishedAt", a.getFinishedAt() == null ? null : a.getFinishedAt().toString());
            out.add(m);
        }
        return out;
    }
}
