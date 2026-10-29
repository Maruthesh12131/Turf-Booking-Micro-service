package com.turfbooking.auth_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;
import com.turfbooking.auth_service.repository.UserRepo;

/**
 * Creates the first admin account at startup, because admins cannot sign themselves up. Off unless both
 * ADMIN_EMAIL and ADMIN_PASSWORD are set. It only ever creates: if an account with that email exists it is left
 * exactly as it is (password and role included), so restarting is harmless and this can never take over an account.
 * The password is never logged.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;
    private final String phone;

    public AdminSeeder(UserRepo userRepo, PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password,
            @Value("${app.admin.name:Administrator}") String name,
            @Value("${app.admin.phone:+910000000000}") String phone) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
        this.name = name;
        this.phone = phone;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** @return true if an admin was created */
    boolean seed() {
        if (email.isEmpty() && password.isEmpty()) {
            return false; // not configured: nothing to do
        }
        // Half a configuration is a mistake worth stopping for, not something to guess around.
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("Admin seeding needs both ADMIN_EMAIL and ADMIN_PASSWORD");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid email address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        User existing = userRepo.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.getRole() == Role.ADMIN) {
                log.info("Admin account {} already exists; leaving it unchanged", email);
            } else {
                log.warn("An account with ADMIN_EMAIL {} already exists with role {}; not changing it", email, existing.getRole());
            }
            return false;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setName(name);
        admin.setPhoneNumber(phone);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        try {
            userRepo.saveAndFlush(admin);
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same moment created it first.
            log.info("Admin account {} was created by another instance", email);
            return false;
        }
        log.info("Created admin account {}", email);
        return true;
    }
}
