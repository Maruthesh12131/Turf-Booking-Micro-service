package com.turfbooking.booking_service.exception;

// Deliberately generic.
public class OwnerAccessDeniedException extends RuntimeException {
    public OwnerAccessDeniedException() {
        super("You do not have permission to perform this action");
    }
}
