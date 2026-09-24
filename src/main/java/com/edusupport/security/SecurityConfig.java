package com.edusupport.security;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .cors(cors -> {})

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authenticationProvider(authenticationProvider)

            .authorizeHttpRequests(auth -> auth

                // Public endpoints
                .requestMatchers(
                    "/api/auth/login",
                    "/api/health"
                ).permitAll()

                // Student dashboard
                .requestMatchers("/api/dashboard/student")
                    .hasRole("STUDENT")

                // Staff dashboard
                .requestMatchers("/api/dashboard/staff")
                    .hasRole("STAFF")

                // Admin dashboard
                .requestMatchers("/api/dashboard/admin")
                    .hasRole("ADMIN")

                // Admin summary
                .requestMatchers("/api/dashboard/summary")
                    .hasRole("ADMIN")

                // Ticket APIs
                .requestMatchers("/api/tickets/**")
                    .authenticated()

                // Everything else
                .anyRequest()
                    .authenticated()
            )

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    jsonAuthenticationEntryPoint()
                )
                .accessDeniedHandler(
                    jsonAccessDeniedHandler()
                )
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${frontend.allowed-origins:http://localhost:4173}")
            String allowedOrigins) {

        CorsConfiguration configuration =
            new CorsConfiguration();

        configuration.setAllowedOrigins(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList()
        );

        configuration.setAllowedMethods(
            Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
            )
        );

        configuration.setAllowedHeaders(
            Arrays.asList(
                "Authorization",
                "Content-Type"
            )
        );

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/api/**",
            configuration
        );

        return source;
    }

    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {

        return (request, response, exception) -> {

            writeError(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "Authentication is required"
            );
        };
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {

        return (request, response, exception) -> {

            writeError(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "You do not have permission to access this resource"
            );
        };
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message)
            throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");

        response.getWriter().write(
            "{\"status\":%d,\"message\":\"%s\"}"
                .formatted(status, message)
        );
    }
}