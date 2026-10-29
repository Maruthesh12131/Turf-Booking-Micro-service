package com.turfbooking.owner_service.event;

// Contract with auth-service (it keeps its own identical copy; the services share no code).
public record OwnerRegisteredEvent(Long userId, String businessName, String phoneNumber) {
}
