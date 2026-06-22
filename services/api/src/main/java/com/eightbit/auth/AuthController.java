package com.eightbit.auth;

import com.eightbit.auth.dto.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Step 1 for both sign-up and login (same details either way): email + roll + username.
     * A one-time code is emailed; finish at /auth/verify-code.
     */
    @PostMapping("/start")
    public AuthResponse start(@Valid @RequestBody RequestCodeRequest req) {
        return auth.start(req.email(), req.rollNumber(), req.username());
    }

    /** Login step 2: exchange the emailed code for a token. */
    @PostMapping("/verify-code")
    public AuthResponse verifyCode(@Valid @RequestBody VerifyCodeRequest req) {
        return auth.verifyCode(req.email(), req.code());
    }
}
