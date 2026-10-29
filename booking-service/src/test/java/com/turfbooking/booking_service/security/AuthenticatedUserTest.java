package com.turfbooking.booking_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedUserTest {

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("tok", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), claims);
    }

    private static Map<String, Object> full() {
        Map<String, Object> c = new HashMap<>();
        c.put("sub", "c@x.com");
        c.put("uid", 42);
        c.put("name", "Cathy");
        c.put("phone", "+919876543210");
        return c;
    }

    @Test
    void readsIdentityFromClaims() {
        assertThat(AuthenticatedUser.from(jwt(full())))
                .isEqualTo(new AuthenticatedUser(42L, "c@x.com", "Cathy", "+919876543210"));
    }

    @Test
    void numericUidOfAnyTypeIsAccepted() {
        Map<String, Object> c = full();
        c.put("uid", 42L);
        assertThat(AuthenticatedUser.from(jwt(c)).userId()).isEqualTo(42L);
    }

    @Test
    void missingOrBlankClaimsMeanLoginAgain() {
        for (String claim : new String[] { "sub", "uid", "name", "phone" }) {
            Map<String, Object> c = full();
            c.remove(claim);
            c.put("filler", "x"); // a Jwt needs at least one claim
            assertThatThrownBy(() -> AuthenticatedUser.from(jwt(c))).isInstanceOf(BadCredentialsException.class);
        }
        Map<String, Object> blank = full();
        blank.put("phone", "  ");
        assertThatThrownBy(() -> AuthenticatedUser.from(jwt(blank))).isInstanceOf(BadCredentialsException.class);
        Map<String, Object> textUid = full();
        textUid.put("uid", "42");
        assertThatThrownBy(() -> AuthenticatedUser.from(jwt(textUid))).isInstanceOf(BadCredentialsException.class);
    }
}
