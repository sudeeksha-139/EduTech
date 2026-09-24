package com.edusupport.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.edusupport.security.JwtService;
import com.edusupport.security.UserPrincipal;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return new LoginResponse(jwtService.generateToken(principal), principal.getId(), principal.getName(),
                principal.getRole());
    }

    public record LoginResponse(String token, Long userId, String name, com.edusupport.user.UserRole role) {
    }
}
