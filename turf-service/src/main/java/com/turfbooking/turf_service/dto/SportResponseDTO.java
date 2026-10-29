package com.turfbooking.turf_service.dto;

/** A sport as shown in responses. (The Sports entity is never returned directly: it points back at its courts.) */
public record SportResponseDTO(Long sportId, String sportName) {
}
