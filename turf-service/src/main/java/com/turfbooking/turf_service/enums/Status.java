package com.turfbooking.turf_service.enums;

/**
 * Real state of a slot, visible to its owner. Customers only ever see FREE or OCCUPIED (see CustomerSlotStatus).
 * HELD and BOOKED always carry a reservation id; FREE and BLOCKED never do (enforced by a database check constraint).
 */
public enum Status {
    FREE,
    // Taken for a booking that is still being finalized.
    HELD,
    BOOKED,
    // Reserved by the owner (e.g. maintenance or an offline booking).
    BLOCKED
}
