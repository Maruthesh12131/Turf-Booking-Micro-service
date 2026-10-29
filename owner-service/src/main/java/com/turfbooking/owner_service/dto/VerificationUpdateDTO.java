package com.turfbooking.owner_service.dto;

import com.turfbooking.owner_service.enums.VerificationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VerificationUpdateDTO {

    // Only VERIFIED or REJECTED can be decided; PENDING is the state before any decision (checked in the service).
    @NotNull(message = "status is required")
    private VerificationStatus status;

    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;
}
