package com.turfbooking.turf_service.enums;

public enum ReservationStatus {
    // Slots are held for the booking, awaiting its confirmation.
    HELD,
    CONFIRMED,
    // The request could not be satisfied; no slot was touched.
    REJECTED,
    // Every slot was given back. Also written as a tombstone if a release arrives before its reserve,
    // so a late reserve is refused instead of re-taking slots nobody wants.
    RELEASED
}
