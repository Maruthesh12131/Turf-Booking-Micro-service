package com.turfbooking.turf_service.dto;

import java.time.LocalDate;

public record SlotGenerationResponseDTO(Long courtNumber, LocalDate fromDate, LocalDate toDate, int slotsCreated) {
}
