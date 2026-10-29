package com.turfbooking.turf_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.turfbooking.turf_service.enums.CustomerSlotStatus;

/** Customer view: FREE or OCCUPIED only. */
public record CustomerTimeSlotDTO(Long timeSlotId, Long courtNumber, LocalDate slotDate, LocalTime startTime,
        LocalTime endTime, BigDecimal price, CustomerSlotStatus status) {
}
