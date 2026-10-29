package com.turfbooking.auth_service.dto;

import com.turfbooking.auth_service.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SignupDTO {
    private String email;
    private String name;
    private String password;
    private Role role;
    // Only used when role is OWNER; businessName falls back to name.
    private String businessName;
    private String phoneNumber;
}
