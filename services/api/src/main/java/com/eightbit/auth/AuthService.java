package com.eightbit.auth;

import com.eightbit.auth.dto.AuthDtos.*;
import com.eightbit.auth.otp.VerificationService;
import com.eightbit.common.config.AppProperties;
import com.eightbit.common.ratelimit.RateLimiter;
import com.eightbit.common.security.JwtService;
import com.eightbit.common.web.ApiException;
import com.eightbit.profile.UserStats;
import com.eightbit.profile.UserStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserStatsRepository stats;
    private final RollNumberParser rollParser;
    private final JwtService jwt;
    private final VerificationService verification;
    private final RateLimiter rateLimiter;
    private final AppProperties props;
    private final String adminLoginCode;

    public AuthService(UserRepository users, UserStatsRepository stats, RollNumberParser rollParser,
                       JwtService jwt, VerificationService verification,
                       RateLimiter rateLimiter, AppProperties props,
                       @org.springframework.beans.factory.annotation.Value("${app.admin.login-code:}")
                       String adminLoginCode) {
        this.users = users;
        this.stats = stats;
        this.rollParser = rollParser;
        this.jwt = jwt;
        this.verification = verification;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.adminLoginCode = adminLoginCode;
    }

    private boolean isAdmin(User u) {
        return u.roleList().contains("ROLE_ADMIN") || u.roleList().contains("ROLE_EDITOR");
    }

    /** The admin master code is accepted in place of the emailed OTP, but only for admins/editors. */
    private boolean isAdminCode(User u, String code) {
        return adminLoginCode != null && !adminLoginCode.isBlank()
                && isAdmin(u) && adminLoginCode.equals(code);
    }

    /**
     * One entry point for both sign-up and login (they collect the same details). Identify by email:
     * an existing account logs in (roll + username must match); a new email creates an account. Either
     * way a one-time code is then emailed and the client finishes at /auth/verify-code.
     */
    @Transactional
    public AuthResponse start(String rawEmail, String rawRoll, String rawUsername) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        if (!rateLimiter.allow("otp-request:" + email, 3, Duration.ofMinutes(2))) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Please wait before requesting another code");
        }
        User existing = users.findByEmail(email).orElse(null);

        // TRIAL GATE (disabled): during the trial only the 2026 batch may sign in. Uncomment to
        // enforce; admins/editors are exempt so they can still manage content.
        // if (existing != null && existing.getBatchYear() != null
        //         && existing.getBatchYear() != TRIAL_BATCH_YEAR && !isAdmin(existing)) {
        //     throw ApiException.unauthorized("TRIAL_2026_ONLY",
        //             "The 8Bit trial is open to the 2026 batch only right now.");
        // }

        if (existing != null) {
            if (existing.isEmailVerified()) {
                // Verified account: username must match, so nobody can hijack it.
                // TRIAL: the roll number is replaced by a batch dropdown, so we no longer match on
                // roll. Original roll+username match (restore when roll numbers return):
                // String roll = rollParser.normalize(rawRoll);
                String username = rawUsername == null ? "" : rawUsername.trim();
                if (/* !existing.getRollNumber().equalsIgnoreCase(roll) || */
                        !existing.getUsername().equalsIgnoreCase(username)) {
                    throw ApiException.unauthorized("BAD_DETAILS", "Those details don't match this email");
                }
                return startSession(existing);
            }
            // Unverified: a signup that never finished its OTP. Let them re-enter (possibly corrected)
            // details and just re-send the code, so an incomplete first attempt never locks them out.
            return resumeUnverified(existing, rawRoll, rawUsername);
        }
        return createAccount(email, rawRoll, rawUsername);
    }

    /** Re-validate + update an unfinished signup's details, then re-send the OTP. */
    private AuthResponse resumeUnverified(User u, String rawRoll, String rawUsername) {
        // TRIAL: rawRoll now carries the batch/programme from the dropdown, not a roll number.
        // Original roll parsing (restore when roll numbers return):
        // String roll = rollParser.normalize(rawRoll);
        // RollNumberParser.Parsed parsed = rollParser.parse(roll); // validates the roll format
        String program = normalizeBatch(rawRoll);
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!username.matches("^[A-Za-z0-9_]{3,30}$")) {
            throw ApiException.badRequest("BAD_USERNAME",
                    "Username must be 3–30 letters, numbers or underscore");
        }
        // Only clash-check against OTHER accounts (this unverified one may already hold these values).
        // Original roll uniqueness check (restore when roll numbers return):
        // if (!roll.equalsIgnoreCase(u.getRollNumber()) && users.existsByRollNumber(roll)) {
        //     throw ApiException.conflict("ROLL_TAKEN", "An account already exists for this roll number");
        // }
        if (!username.equalsIgnoreCase(u.getUsername()) && users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }
        // u.setRollNumber(roll);  // original — keep the placeholder set at sign-up during the trial
        u.setUsername(username);
        u.setBatchYear(TRIAL_BATCH_YEAR);
        u.setProgram(program);
        users.save(u);
        return startSession(u);
    }

    // ----------------------------------------------------------------------------------------------
    // TRIAL MODE — the 2026 juniors haven't been assigned roll numbers yet, so they pick their batch
    // from a dropdown instead. Everything below is temporary; the original roll-number flow is kept
    // (commented) throughout this class so it can be switched back on once roll numbers are issued.
    // ----------------------------------------------------------------------------------------------
    private static final int TRIAL_BATCH_YEAR = 2026;
    /** Allowed batch selections — must match the Batch War cohorts in application.yml exactly. */
    private static final java.util.Set<String> TRIAL_PROGRAMS = java.util.Set.of(
            "iMTech CSE", "iMTech ECE", "BTech CSE", "BTech ECE", "BTech AI & DS");

    /** Validate the batch the user picked and return it as the stored programme name. */
    private String normalizeBatch(String rawBatch) {
        String p = rawBatch == null ? "" : rawBatch.trim();
        if (!TRIAL_PROGRAMS.contains(p)) {
            throw ApiException.badRequest("INVALID_BATCH", "Please select your batch");
        }
        return p;
    }

    /** Unique, non-null filler for the (still unique + NOT NULL) roll_number column. Not shown to users. */
    private String syntheticRoll() {
        return "J26-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private AuthResponse createAccount(String email, String rawRoll, String rawUsername) {
        // TRIAL: rawRoll now carries the batch/programme from the dropdown, not a roll number.
        // Original roll parsing (restore when roll numbers return):
        // String roll = rollParser.normalize(rawRoll);
        // RollNumberParser.Parsed parsed = rollParser.parse(roll);  // validates the roll format
        String program = normalizeBatch(rawRoll);
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!username.matches("^[A-Za-z0-9_]{3,30}$")) {
            throw ApiException.badRequest("BAD_USERNAME",
                    "Username must be 3–30 letters, numbers or underscore");
        }
        // Require the configured college domain (default @iiitb.ac.in) so batch scores stay honest.
        String domain = props.getOtp().getEmailDomain();
        if (domain != null && !domain.isBlank() && !email.endsWith("@" + domain.toLowerCase())) {
            throw ApiException.badRequest("INVALID_EMAIL", "Use your @" + domain + " email");
        }
        // Original roll uniqueness check (restore when roll numbers return):
        // if (users.existsByRollNumber(roll)) {
        //     throw ApiException.conflict("ROLL_TAKEN", "An account already exists for this roll number");
        // }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }

        User u = new User();
        // TRIAL: roll_number is unique + NOT NULL, so fill it with a unique placeholder while the
        // juniors have none. Original: u.setRollNumber(roll);
        u.setRollNumber(syntheticRoll());
        u.setUsername(username);
        u.setBatchYear(TRIAL_BATCH_YEAR);       // original: parsed.batchYear()
        u.setProgram(program);                  // original: parsed.program()
        u.setEmail(email);
        u.setRoles("ROLE_USER");
        u.setEmailVerified(false);  // verified by completing the OTP login
        u = users.save(u);
        stats.save(new UserStats(u.getId()));  // stats row up-front so profile reads never NPE
        return startSession(u);
    }

    /**
     * Login step 2: verify the code and issue a token. Admins/editors may type the admin master code
     * (configured, secret) instead of an emailed code, so they're never locked out by email delivery.
     */
    @Transactional
    public AuthResponse verifyCode(String rawEmail, String code) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        User u = users.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("BAD_DETAILS", "Those details don't match an account"));
        if (!isAdminCode(u, code)) {
            verification.check(u.getId(), code);
        }
        if (!u.isEmailVerified()) {
            u.setEmailVerified(true);
            users.save(u);
        }
        return token(u);
    }

    /** Always email a one-time code and tell the client to verify it. No bypass — secure everywhere. */
    private AuthResponse startSession(User u) {
        verification.issue(u);
        return new AuthResponse(true, null, null);
    }

    public boolean isVerified(long userId) {
        return users.findById(userId).map(User::isEmailVerified).orElse(true);
    }

    private AuthResponse token(User u) {
        String accessToken = jwt.issue(u.getId(), u.getUsername(), u.getBatchYear(), u.roleList());
        return new AuthResponse(false, accessToken, toDto(u));
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getRollNumber(),
                u.getBatchYear(), u.getProgram(), u.isEmailVerified(), u.roleList());
    }
}
