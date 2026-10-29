package com.turfbooking.booking_service.event;

import java.util.List;

/** booking-service to turf-service: take all of these slots for the booking, or none. */
public record ReserveSlotsCommand(Long reservationId, List<Long> slotIds) {
}
