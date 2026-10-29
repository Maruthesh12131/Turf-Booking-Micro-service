package com.turfbooking.auth_service.dto;

import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.enums.Role;

/** What the API says about an account. Deliberately has no password hash: the entity must never be returned directly. */
public record UserResponseDTO(Integer userId, String email, String name, String phoneNumber, Role role) {

    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getUserId(), user.getEmail(), user.getName(), user.getPhoneNumber(), user.getRole());
    }
}
