package com.eightbit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response payloads for the auth module. */
public class AuthDtos {

    /**
     * Step 1 for both sign-up and login (passwordless): email + roll + username. A new email creates
     * an account; an existing email logs in (roll + username must match). A one-time code follows.
     */
    public record RequestCodeRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank String rollNumber,
            @NotBlank String username
    ) {}

    /** Login step 2: exchange the emailed code for a token. */
    public record VerifyCodeRequest(@NotBlank @Email String email, @NotBlank String code) {}

    public record UserDto(
            Long id,
            String username,
            String rollNumber,
            Integer batchYear,
            String program,
            boolean emailVerified,
            List<String> roles
    ) {}

    /**
     * otpRequired=true means a code was emailed and the client must call /auth/verify-code next
     * (accessToken/user are null). Otherwise the token is returned immediately (OTP off / verified).
     */
    public record AuthResponse(boolean otpRequired, String accessToken, UserDto user) {}
}
