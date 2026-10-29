package com.turfbooking.booking_service.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, read from the validated token and never from the request. A token that lacks a claim we depend on
 * (for example one issued before name and phone were added) is treated as unauthenticated: logging in again fixes it.
 */
public record AuthenticatedUser(Long userId, String email, String name, String phone) {

    public static AuthenticatedUser from(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        String email = jwt.getSubject();
        String name = jwt.getClaimAsString("name");
        String phone = jwt.getClaimAsString("phone");
        if (!(uid instanceof Number) || isBlank(email) || isBlank(name) || isBlank(phone)) {
            throw new BadCredentialsException("Token is missing required claims; please log in again");
        }
        return new AuthenticatedUser(((Number) uid).longValue(), email, name, phone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
