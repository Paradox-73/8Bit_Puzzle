package com.eightbit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response payloads for the auth module. */
public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String rollNumber,
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "may only contain letters, numbers and underscore")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record LoginRequest(
            @NotBlank String rollNumber,
            @NotBlank String password
    ) {}

    public record UserDto(
            Long id,
            String username,
            String rollNumber,
            Integer batchYear,
            String program,
            List<String> roles
    ) {}

    public record AuthResponse(String accessToken, UserDto user) {}
}
