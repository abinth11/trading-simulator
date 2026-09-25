package com.tradingsim.user.config;

import com.tradingsim.user.entity.User;
import com.tradingsim.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the operator account used to sign in to the admin dashboard.
 *
 * Runs once per startup and only creates the account if the email is unused — it never
 * changes an existing user's role or password. Leave the email or password blank to skip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email:}")
    private String email;

    @Value("${admin.bootstrap.username:admin}")
    private String username;

    @Value("${admin.bootstrap.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("Admin bootstrap skipped: admin.bootstrap.email / admin.bootstrap.password not set");
            return;
        }

        userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (existing.getRole() != User.Role.ADMIN) {
                        log.warn("Admin bootstrap: {} exists with role {} — not changing it", email, existing.getRole());
                    }
                },
                () -> {
                    userRepository.save(User.builder()
                            .email(email)
                            .username(username)
                            .passwordHash(passwordEncoder.encode(password))
                            .role(User.Role.ADMIN)
                            .build());
                    log.info("Admin bootstrap: created admin account {}", email);
                });
    }
}
