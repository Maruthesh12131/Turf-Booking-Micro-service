package com.turfbooking.turf_service.exception;

public class TurfNotFoundException extends RuntimeException {

    public TurfNotFoundException(Long turfId) {
        super("Turf not found with id: " + turfId);
    }

    public TurfNotFoundException(String message) {
        super(message);
    }
}
