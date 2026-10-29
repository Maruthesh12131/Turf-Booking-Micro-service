package com.turfbooking.owner_service.event;

/** version increases with every admin decision on the owner, so a consumer can drop stale or reordered deliveries. */
public record OwnerVerificationChangedEvent(Long ownerId, String status, Long version) {
}
