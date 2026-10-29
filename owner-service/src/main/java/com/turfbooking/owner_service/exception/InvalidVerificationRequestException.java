package com.turfbooking.owner_service.exception;

public class InvalidVerificationRequestException extends RuntimeException {
    public InvalidVerificationRequestException(String message) {
        super(message);
    }
}
