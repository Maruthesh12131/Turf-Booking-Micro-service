package com.turfbooking.turf_service.exception;

// Deliberately generic: never say who owns the resource.
public class TurfAccessDeniedException extends RuntimeException {
    public TurfAccessDeniedException() {
        super("You do not have permission to perform this action");
    }
}
