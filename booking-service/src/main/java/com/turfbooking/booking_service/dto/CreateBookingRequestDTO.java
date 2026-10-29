package com.turfbooking.booking_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Only slot ids are accepted. Who is booking comes from the token; court, date, times and prices come from
 * turf-service. All the slots must be on one court and one date (checked when they are reserved).
 */
public record CreateBookingRequestDTO(
        @NotEmpty(message = "slotIds must not be empty")
        @Size(max = 10, message = "A booking can have at most 10 slots")
        List<@NotNull(message = "slotIds must not contain null") Long> slotIds) {
}
