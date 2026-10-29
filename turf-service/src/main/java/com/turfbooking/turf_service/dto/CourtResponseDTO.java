package com.turfbooking.turf_service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CourtResponseDTO {
    private Long courtNumber;
    private Long amountPerHour;
    private Long length;
    private Long width;
    // The sports the owner chose for this court; empty if none were chosen.
    private List<SportResponseDTO> sports;
}
