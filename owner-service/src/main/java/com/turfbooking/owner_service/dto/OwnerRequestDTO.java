package com.turfbooking.owner_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class OwnerRequestDTO {

    @NotBlank(message = "businessName is required")
    @Size(max = 150, message = "businessName must be at most 150 characters")
    private String businessName;

    @NotBlank(message = "phoneNumber is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phoneNumber must be 7-15 digits, optionally starting with +")
    private String phoneNumber;
}
