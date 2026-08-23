package com.project.ticketflow.config;

import com.project.ticketflow.entity.User;
import com.project.ticketflow.enums.Role;
import com.project.ticketflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Seeds a single ADMIN account from env vars on startup if none exists yet — admins are
 * never self-registered (see AuthServiceImpl.SELF_REGISTERABLE_ROLES), so this is the only
 * way to get the first one in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ticketflow.admin.email:}")
    private String adminEmail;

    @Value("${ticketflow.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        User admin = User.builder()
                .name("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .roles(Set.of(Role.ADMIN))
                .build();
        userRepository.save(admin);
        log.info("Seeded bootstrap ADMIN account: {}", adminEmail);
    }
}
