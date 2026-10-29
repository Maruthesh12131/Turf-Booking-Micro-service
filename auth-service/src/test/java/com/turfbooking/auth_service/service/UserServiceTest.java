package com.turfbooking.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.turfbooking.auth_service.dto.SignupDTO;
import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;
import com.turfbooking.auth_service.event.OwnerRegisteredEvent;
import com.turfbooking.auth_service.messaging.OwnerEventPublishException;
import com.turfbooking.auth_service.messaging.OwnerEventPublisher;
import com.turfbooking.auth_service.repository.UserRepo;

class UserServiceTest {

    UserRepo repo = mock(UserRepo.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    OwnerEventPublisher events = mock(OwnerEventPublisher.class);
    UserService service = new UserService(repo, encoder, events);

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("hash");
        when(repo.saveAndFlush(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUserId(42);
            return u;
        });
    }

    private static SignupDTO signup(Role role, String businessName, String phone) {
        return new SignupDTO("o@x.com", "Owner Name", "pw", role, businessName, phone);
    }

    @Test
    void ownerSignupPublishesEventWithUserIdAndPhone() {
        service.saveUser(signup(Role.OWNER, "Arena Co", "+919876543210"));

        ArgumentCaptor<OwnerRegisteredEvent> event = ArgumentCaptor.forClass(OwnerRegisteredEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue()).isEqualTo(new OwnerRegisteredEvent(42L, "Arena Co", "+919876543210"));
    }

    @Test
    void businessNameDefaultsToNameWhenBlank() {
        service.saveUser(signup(Role.OWNER, " ", "+919876543210"));

        ArgumentCaptor<OwnerRegisteredEvent> event = ArgumentCaptor.forClass(OwnerRegisteredEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().businessName()).isEqualTo("Owner Name");
    }

    @Test
    void anyoneWithoutValidPhoneIsRejectedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service.saveUser(signup(Role.CUSTOMER, null, null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveUser(signup(Role.CUSTOMER, null, "12"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveUser(signup(Role.OWNER, "Arena", null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveUser(signup(Role.OWNER, "Arena", "abc"))).isInstanceOf(ResponseStatusException.class);
        verify(repo, never()).saveAndFlush(any());
        verify(events, never()).publish(any());
    }

    @Test
    void customerSignupStoresPhoneAndPublishesNothing() {
        service.saveUser(signup(Role.CUSTOMER, null, "9876543210"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("9876543210");
        verify(events, never()).publish(any());
    }

    @Test
    void adminSelfRegistrationIsForbidden() {
        assertThatThrownBy(() -> service.saveUser(signup(Role.ADMIN, null, null))).isInstanceOf(ResponseStatusException.class);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void brokerFailureFailsTheSignupWith503() {
        org.mockito.Mockito.doThrow(new OwnerEventPublishException("down", null)).when(events).publish(any());

        assertThatThrownBy(() -> service.saveUser(signup(Role.OWNER, "Arena", "+919876543210")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }

    @Test
    void unknownEmailAtLoginIsAUsernameNotFoundNotAGenericError() {
        when(repo.findByEmail("nobody@x.com")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("nobody@x.com"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }
}
