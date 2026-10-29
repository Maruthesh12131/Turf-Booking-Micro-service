package com.turfbooking.turf_service.dto;

import com.turfbooking.turf_service.enums.Status;

import jakarta.validation.constraints.NotNull;

/** An owner may only set FREE or BLOCKED; HELD and BOOKED belong to the booking workflow. */
public record SlotStatusUpdateDTO(@NotNull(message = "status is required") Status status) {
}
