package com.turfbooking.booking_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** cancelSlotIds absent or empty cancels the whole booking; otherwise only those slots (partial cancellation). */
public record CancelBookingRequestDTO(
        @Size(max = 10, message = "At most 10 slots")
        List<@NotNull(message = "cancelSlotIds must not contain null") Long> cancelSlotIds) {
}
