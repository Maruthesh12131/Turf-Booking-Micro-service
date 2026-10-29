package com.turfbooking.turf_service.event;

/** booking-service to turf-service: the booking is confirmed, turn the held slots into booked slots. */
public record ConfirmReservationCommand(Long reservationId) {
}
