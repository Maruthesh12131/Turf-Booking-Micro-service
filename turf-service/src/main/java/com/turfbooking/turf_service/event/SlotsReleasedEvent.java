package com.turfbooking.turf_service.event;

import java.util.List;

/** turf-service to booking-service: these slots are no longer held for the reservation. */
public record SlotsReleasedEvent(Long reservationId, List<Long> slotIds) {
}
