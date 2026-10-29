package com.turfbooking.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;
import com.turfbooking.auth_service.repository.UserRepo;

class AdminSeederTest {

    static final String PASSWORD = "a-long-enough-password";

    UserRepo repo = mock(UserRepo.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    AdminSeeder seeder(String email, String password) {
        when(encoder.encode(any())).thenReturn("hashed");
        return new AdminSeeder(repo, encoder, email, password, "Administrator", "+910000000000");
    }

    @Test
    void createsAnAdminWithAHashedPassword() {
        assertThat(seeder("admin@x.com", PASSWORD).seed()).isTrue();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@x.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed").isNotEqualTo(PASSWORD);
        assertThat(saved.getValue().getPhoneNumber()).isNotBlank();
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertThat(seeder("", "").seed()).isFalse();
        assertThat(seeder(null, null).seed()).isFalse();
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void halfAConfigurationStopsStartup() {
        assertThatThrownBy(() -> seeder("admin@x.com", "").seed()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder("", PASSWORD).seed()).isInstanceOf(IllegalStateException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void weakPasswordOrBadEmailStopsStartup() {
        assertThatThrownBy(() -> seeder("admin@x.com", "short").seed()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder("not-an-email", PASSWORD).seed()).isInstanceOf(IllegalStateException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void existingAccountIsNeverModifiedWhateverItsRole() {
        for (Role role : new Role[] { Role.ADMIN, Role.CUSTOMER, Role.OWNER }) {
            User existing = new User(1, "admin@x.com", "Someone", "+911234567", "their-hash", role);
            when(repo.findByEmail("admin@x.com")).thenReturn(Optional.of(existing));

            assertThat(seeder("admin@x.com", PASSWORD).seed()).isFalse();

            assertThat(existing.getRole()).isEqualTo(role);              // a customer is not promoted
            assertThat(existing.getPasswordHash()).isEqualTo("their-hash"); // the password is not reset
        }
        verify(repo, never()).saveAndFlush(any());
        verify(repo, never()).save(any());
    }

    @Test
    void anotherInstanceCreatingItFirstIsNotAnError() {
        when(repo.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));
        assertThat(seeder("admin@x.com", PASSWORD).seed()).isFalse();
    }
}
