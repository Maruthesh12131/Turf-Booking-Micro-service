package com.turfbooking.booking_service.enums;

public enum BookingStatus {
    // Saved, waiting for turf-service to say whether it could take every slot.
    PENDING_RESERVATION,
    CONFIRMED,
    // Some, but not all, of the booked slots were cancelled; the rest stand.
    PARTIALLY_CANCELLED,
    CANCELLED,
    // Turf-service could not take all the slots (or the request was invalid); nothing was reserved.
    REJECTED
}
