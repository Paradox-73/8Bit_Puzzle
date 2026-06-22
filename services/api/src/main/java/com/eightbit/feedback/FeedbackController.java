package com.eightbit.feedback;

import com.eightbit.auth.otp.EmailSender;
import com.eightbit.common.ratelimit.RateLimiter;
import com.eightbit.common.security.AuthUser;
import com.eightbit.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Player feedback and bug reports: persisted for the team and emailed on arrival. */
@RestController
public class FeedbackController {

    private static final Set<String> TYPES = Set.of("feedback", "bug");
    private static final int MAX_LEN = 2000;

    private final FeedbackRepository repo;
    private final EmailSender email;
    private final RateLimiter rateLimiter;
    private final String teamEmail;

    public FeedbackController(FeedbackRepository repo, EmailSender email, RateLimiter rateLimiter,
                              @Value("${app.feedback.to:8bit@iiitb.ac.in}") String teamEmail) {
        this.repo = repo;
        this.email = email;
        this.rateLimiter = rateLimiter;
        this.teamEmail = teamEmail;
    }

    @PostMapping("/feedback")
    @Transactional
    public Map<String, Object> submit(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal AuthUser user) {
        String type = str(body.get("type")).toLowerCase();
        if (!TYPES.contains(type)) {
            throw ApiException.badRequest("BAD_TYPE", "Type must be 'feedback' or 'bug'");
        }
        String message = str(body.get("message")).trim();
        if (message.isBlank()) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "Please tell us a bit more");
        }
        if (message.length() > MAX_LEN) message = message.substring(0, MAX_LEN);
        String context = str(body.get("context")).trim();
        if (context.length() > 500) context = context.substring(0, 500);

        // Light anti-spam: a handful of submissions per 10 minutes per user.
        if (!rateLimiter.allow("feedback:" + user.id(), 5, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Thanks! You've sent a few already — try later.");
        }

        Feedback fb = repo.save(new Feedback(user.id(), user.username(), type, message,
                context.isBlank() ? null : context));

        String label = type.equals("bug") ? "Bug report" : "Feedback";
        email.send(teamEmail, "[8Bit] " + label + " from " + user.username(),
                label + " #" + fb.getId() + "\n"
                + "From: " + user.username() + " (id=" + user.id() + ")\n"
                + (context.isBlank() ? "" : "Context: " + context + "\n")
                + "\n" + message);

        return Map.of("ok", true, "id", fb.getId());
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
