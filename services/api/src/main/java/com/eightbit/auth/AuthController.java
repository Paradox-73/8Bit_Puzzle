package com.eightbit.auth;

import com.eightbit.auth.dto.AuthDtos.*;
import com.eightbit.common.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOtp(@Valid @RequestBody VerifyOtpRequest req,
                                         @AuthenticationPrincipal AuthUser user) {
        auth.verifyOtp(user.id(), req.code());
        return Map.of("verified", true);
    }

    @PostMapping("/resend-otp")
    public Map<String, Object> resendOtp(@AuthenticationPrincipal AuthUser user) {
        auth.resendOtp(user.id());
        return Map.of("sent", true);
    }
}
