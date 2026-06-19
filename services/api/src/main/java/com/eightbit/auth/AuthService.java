package com.eightbit.auth;

import com.eightbit.auth.dto.AuthDtos.*;
import com.eightbit.common.security.JwtService;
import com.eightbit.common.web.ApiException;
import com.eightbit.profile.UserStats;
import com.eightbit.profile.UserStatsRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserStatsRepository stats;
    private final RollNumberParser rollParser;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, UserStatsRepository stats, RollNumberParser rollParser,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.stats = stats;
        this.rollParser = rollParser;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String roll = rollParser.normalize(req.rollNumber());
        RollNumberParser.Parsed parsed = rollParser.parse(roll);

        if (users.existsByRollNumber(roll)) {
            throw ApiException.conflict("ROLL_TAKEN", "An account already exists for this roll number");
        }
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is taken");
        }

        User u = new User();
        u.setRollNumber(roll);
        u.setUsername(req.username());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setBatchYear(parsed.batchYear());
        u.setProgram(parsed.program());
        u.setRoles("ROLE_USER");
        u = users.save(u);

        // Stats row up-front so streak/profile reads never NPE.
        stats.save(new UserStats(u.getId()));

        return toAuthResponse(u);
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
                u.getBatchYear(), u.getProgram(), u.roleList());
    }
}
