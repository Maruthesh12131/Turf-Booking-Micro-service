package com.turfbooking.turf_service.dto;

import java.time.LocalTime;
import java.util.List;

import com.turfbooking.turf_service.entity.Courts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
public class TurfDTO {
    @NotBlank(message = "name is required")
    private String name;

    @NotBlank (message = "location is required")
    private String location;

    private String description;

    private List<Courts> courts;

    @NotNull (message = "opening time is required")
    private LocalTime openingTime;

    @NotNull (message = "closing time is required")
    private LocalTime closingTime;
    
}
