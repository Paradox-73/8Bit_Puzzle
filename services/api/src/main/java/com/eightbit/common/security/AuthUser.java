package com.eightbit.common.security;

/**
 * The authenticated principal placed on the SecurityContext. Controllers read it via
 * {@code @AuthenticationPrincipal AuthUser user}.
 */
public record AuthUser(long id, String username, Integer batchYear) {
}
