package com.turfbooking.turf_service.exception;

public class SlotsUnavailableException extends RuntimeException {
    public SlotsUnavailableException(String message) {
        super(message);
    }
}
