package com.turfbooking.turf_service.event;

/** turf-service to booking-service: nothing was reserved. code is UNAVAILABLE (try other slots) or INVALID (bad request). */
public record SlotsRejectedEvent(Long reservationId, String code, String message) {
}
