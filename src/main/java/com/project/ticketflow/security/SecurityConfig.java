package com.project.ticketflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.security.SecureRandom;
import java.util.List;

import static com.project.ticketflow.enums.Role.ADMIN;
import static com.project.ticketflow.enums.Role.ORGANISER;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                             JwtAuthFilter jwtAuthFilter,
                                             HandlerExceptionResolver handlerExceptionResolver) throws Exception {
        httpSecurity
                .csrf(csrfConfig -> csrfConfig.disable())
                .cors(corsConfig -> corsConfig.configurationSource(corsConfigurationSource()))
                .sessionManagement(sessionConfig -> sessionConfig
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/logout").authenticated()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/events/**").permitAll()
                        .requestMatchers("/venues/**").permitAll()
                        .requestMatchers("/shows/*/seatmap").permitAll()
                        // seat availability is public data (same as the seatmap REST
                        // endpoint above) — this is the WebSocket handshake path for the
                        // live seatmap feed, not an app endpoint
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/admin/**").hasRole(ADMIN.name())
                        .requestMatchers("/organiser/**").hasRole(ORGANISER.name())
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> {
                    ex.authenticationEntryPoint((request, response, authException) ->
                            handlerExceptionResolver.resolveException(request, response, null, authException)
                    );
                    ex.accessDeniedHandler((request, response, accessDeniedException) ->
                            handlerExceptionResolver.resolveException(request, response, null, accessDeniedException)
                    );
                })
        ;

        return httpSecurity.build();
    }

    // Allow-all for now — tighten to specific frontend origin(s) once one exists.
    // allowCredentials is left false (default) since auth is a Bearer token, not a cookie,
    // which is what lets us pair a wildcard origin with "*" methods/headers here.
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

}
