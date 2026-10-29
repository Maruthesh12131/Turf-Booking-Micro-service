package com.turfbooking.owner_service.exception;

public class InvalidVerificationTransitionException extends RuntimeException {
    public InvalidVerificationTransitionException(String message) {
        super(message);
    }
}
