package com.turfbooking.turf_service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Slots are generated for 7 consecutive days starting at startDate. */
public record CreateTimeSlotsRequestDTO(
        @NotNull(message = "turfId is required") Long turfId,
        @NotNull(message = "startDate is required") LocalDate startDate) {
}
