package com.edusupport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edusupport.security.JwtService;
import com.edusupport.security.UserPrincipal;
import com.edusupport.user.User;
import com.edusupport.user.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock Authentication authentication;

    private AuthService authService;
    private UserPrincipal student;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtService);
        student = UserPrincipal.from(new User("Asha Student", "asha@example.edu", "{bcrypt}hash", UserRole.STUDENT));
    }

    @Test
    void returnsTokenAndSafeUserProfileForValidStudentLogin() {
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("asha@example.edu", "secret")))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(student);
        when(jwtService.generateToken(student)).thenReturn("jwt-token");

        AuthService.LoginResponse response = authService.login("asha@example.edu", "secret");

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.name()).isEqualTo("Asha Student");
        assertThat(response.role()).isEqualTo(UserRole.STUDENT);
        assertThat(response).hasNoNullFieldsOrPropertiesExcept("userId");
    }

    @Test
    void rejectsInvalidPassword() {
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("asha@example.edu", "wrong")))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login("asha@example.edu", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnknownUser() {
        when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("missing@example.edu", "secret")))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login("missing@example.edu", "secret"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
