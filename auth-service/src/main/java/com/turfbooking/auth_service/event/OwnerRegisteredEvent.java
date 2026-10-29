package com.turfbooking.auth_service.event;

// Contract with owner-service (it keeps its own identical copy; the services share no code).
// userId is the auth-service user id, which owner-service stores as owners.user_id.
public record OwnerRegisteredEvent(Long userId, String businessName, String phoneNumber) {
}
