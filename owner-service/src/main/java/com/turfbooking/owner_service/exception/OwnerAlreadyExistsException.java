package com.turfbooking.owner_service.exception;

public class OwnerAlreadyExistsException extends RuntimeException {
    public OwnerAlreadyExistsException(String message) {
        super(message);
    }
}
