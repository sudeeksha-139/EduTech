package com.edusupport.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.edusupport.user.User;
import com.edusupport.user.UserRole;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "edusupport-test-secret-that-is-at-least-32-bytes-long", 3_600_000);

    @Test
    void validatesTokenForTheIssuingUser() {
        UserPrincipal principal = UserPrincipal.from(
                new User("Asha Student", "asha@example.edu", "hash", UserRole.STUDENT));
        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void rejectsMissingOrInvalidToken() {
        UserPrincipal principal = UserPrincipal.from(
                new User("Asha Student", "asha@example.edu", "hash", UserRole.STUDENT));

        assertThat(jwtService.isTokenValid("not-a-jwt", principal)).isFalse();
    }
}
