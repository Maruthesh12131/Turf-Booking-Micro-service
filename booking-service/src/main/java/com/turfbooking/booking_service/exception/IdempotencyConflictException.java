package com.turfbooking.booking_service.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("This Idempotency-Key was already used for a different request");
    }
}
