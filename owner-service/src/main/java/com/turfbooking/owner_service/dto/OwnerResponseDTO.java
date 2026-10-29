package com.turfbooking.owner_service.dto;

import java.time.Instant;

import com.turfbooking.owner_service.enums.VerificationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OwnerResponseDTO {
    private Long ownerId;
    private String businessName;
    private String phoneNumber;
    private VerificationStatus verificationStatus;
    private Instant createdAt;
}
