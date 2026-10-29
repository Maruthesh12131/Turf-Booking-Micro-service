package com.turfbooking.turf_service.event;

public record OwnerVerificationChangedEvent(Long ownerId, String status, Long version) {
}
