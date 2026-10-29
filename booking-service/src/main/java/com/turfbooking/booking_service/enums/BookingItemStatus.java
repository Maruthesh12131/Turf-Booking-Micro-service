package com.turfbooking.booking_service.enums;

public enum BookingItemStatus {
    // Slot requested, reservation not yet answered.
    PENDING,
    ACTIVE,
    // Cancellation asked of turf-service, not yet confirmed. The slot still counts as this booking's.
    RELEASE_PENDING,
    CANCELLED,
    REJECTED
}
