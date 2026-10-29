package com.turfbooking.turf_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.turfbooking.turf_service.enums.Status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Owner view: the real slot status. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TimeSlotResponseDTO {
    private Long timeSlotId;
    private Long courtNumber;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    private Status status;
}
