package com.turfbooking.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.turfbooking.auth_service.dto.LoginDTO;
import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;
import com.turfbooking.auth_service.repository.UserRepo;

class AuthServiceTest {

    AuthenticationManager authManager = mock(AuthenticationManager.class);
    JwtEncoder encoder = mock(JwtEncoder.class);
    UserRepo repo = mock(UserRepo.class);
    AuthService service = new AuthService(authManager, encoder, repo);

    @Test
    void tokenCarriesIdentityNameAndPhone() {
        User user = new User(42, "c@x.com", "Cathy", "+919876543210", "hash", Role.CUSTOMER);
        when(authManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("c@x.com", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        when(repo.findByEmail("c@x.com")).thenReturn(Optional.of(user));
        when(encoder.encode(any())).thenReturn(new Jwt("tok", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "c@x.com")));

        assertThat(service.loginUser(new LoginDTO("c@x.com", "pw"))).isEqualTo("tok");

        ArgumentCaptor<JwtEncoderParameters> params = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder).encode(params.capture());
        Map<String, Object> claims = params.getValue().getClaims().getClaims();
        assertThat(claims).containsEntry("sub", "c@x.com").containsEntry("uid", 42).containsEntry("role", "ROLE_CUSTOMER")
                .containsEntry("name", "Cathy").containsEntry("phone", "+919876543210");
        assertThat(claims).doesNotContainKey("passwordHash");
    }
}
