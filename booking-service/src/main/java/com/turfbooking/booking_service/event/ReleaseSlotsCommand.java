package com.turfbooking.booking_service.event;

import java.util.List;

/** booking-service to turf-service: give slots back. slotIds null means every slot of the reservation. */
public record ReleaseSlotsCommand(Long reservationId, List<Long> slotIds) {
}
