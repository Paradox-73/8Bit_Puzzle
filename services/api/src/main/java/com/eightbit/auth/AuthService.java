package com.eightbit.auth;

import com.eightbit.auth.dto.AuthDtos.*;
import com.eightbit.auth.otp.VerificationService;
import com.eightbit.common.config.AppProperties;
import com.eightbit.common.ratelimit.RateLimiter;
import com.eightbit.common.security.JwtService;
import com.eightbit.common.web.ApiException;
import com.eightbit.profile.UserStats;
import com.eightbit.profile.UserStatsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserStatsRepository stats;
    private final RollNumberParser rollParser;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final VerificationService verification;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    public AuthService(UserRepository users, UserStatsRepository stats, RollNumberParser rollParser,
                       PasswordEncoder encoder, JwtService jwt, VerificationService verification,
                       RateLimiter rateLimiter, AppProperties props) {
        this.users = users;
        this.stats = stats;
        this.rollParser = rollParser;
        this.encoder = encoder;
        this.jwt = jwt;
        this.verification = verification;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String roll = rollParser.normalize(req.rollNumber());
        RollNumberParser.Parsed parsed = rollParser.parse(roll);

        String email = req.email() == null ? "" : req.email().trim().toLowerCase();

        if (users.existsByRollNumber(roll)) {
            throw ApiException.conflict("ROLL_TAKEN", "An account already exists for this roll number");
        }
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }
        // One email -> one account (a defence against fake-account batch stuffing).
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account already uses this email");
        }
        // When verification is on, require the college domain so batch scores stay honest.
        String domain = props.getOtp().getEmailDomain();
        if (props.getOtp().isEnabled() && domain != null && !domain.isBlank()
                && !email.endsWith("@" + domain.toLowerCase())) {
            throw ApiException.badRequest("INVALID_EMAIL", "Use your @" + domain + " email");
        }

        User u = new User();
        u.setRollNumber(roll);
        u.setUsername(req.username());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setBatchYear(parsed.batchYear());
        u.setProgram(parsed.program());
        u.setEmail(email);
        u.setRoles("ROLE_USER");
        // When OTP is off (dev default) accounts are auto-verified; when on, they must verify
        // before counting toward batch scores.
        u.setEmailVerified(!props.getOtp().isEnabled());
        u = users.save(u);

        // Stats row up-front so streak/profile reads never NPE.
        stats.save(new UserStats(u.getId()));

        if (props.getOtp().isEnabled()) {
            verification.issue(u);
        }
        return toAuthResponse(u);
    }

    @Transactional
    public void verifyOtp(long userId, String code) {
        verification.check(userId, code);
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        u.setEmailVerified(true);
        users.save(u);
    }

    @Transactional(readOnly = true)
    public void resendOtp(long userId) {
        if (!props.getOtp().isEnabled()) {
            throw ApiException.badRequest("OTP_NOT_PENDING", "Email verification is not required");
        }
        if (!rateLimiter.allow("otp-resend:" + userId, 1, Duration.ofSeconds(60))) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "Please wait before requesting another code");
        }
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (u.isEmailVerified()) {
            throw ApiException.badRequest("OTP_NOT_PENDING", "Your email is already verified");
        }
        verification.issue(u);
    }

    public boolean isVerified(long userId) {
        return users.findById(userId).map(User::isEmailVerified).orElse(true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String roll = rollParser.normalize(req.rollNumber());
        User u = users.findByRollNumber(roll)
                .orElseThrow(() -> ApiException.unauthorized("BAD_CREDENTIALS", "Invalid roll number or password"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Invalid roll number or password");
        }
        return toAuthResponse(u);
    }

    private AuthResponse toAuthResponse(User u) {
        String token = jwt.issue(u.getId(), u.getUsername(), u.getBatchYear(), u.roleList());
        return new AuthResponse(token, toDto(u));
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getRollNumber(),
                u.getBatchYear(), u.getProgram(), u.isEmailVerified(), u.roleList());
    }
}
