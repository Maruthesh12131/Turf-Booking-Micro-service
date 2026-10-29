package com.turfbooking.turf_service.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** turf-service to booking-service: every requested slot is held. Prices are the ones stored on the slots. */
public record SlotsReservedEvent(Long reservationId, Long turfId, Long ownerId, Long courtNumber, LocalDate slotDate,
        List<ReservedSlot> slots) {

    public record ReservedSlot(Long slotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime, BigDecimal price) {
    }
}
