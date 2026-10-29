package com.turfbooking.turf_service.dto;

import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import com.turfbooking.turf_service.entity.Courts;
import com.turfbooking.turf_service.enums.TurfStatus;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurfDetailsDTO {

    private Long turfId;
    private String name;
    private String location;
    private String description;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private List<CourtResponseDTO> courts;
    private TurfStatus status;
    
}
