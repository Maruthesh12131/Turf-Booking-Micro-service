package com.turfbooking.turf_service.exception;

public class OwnerServiceUnavailableException extends RuntimeException {
    public OwnerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
