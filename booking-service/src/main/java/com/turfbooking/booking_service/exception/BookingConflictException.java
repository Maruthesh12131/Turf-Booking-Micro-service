package com.turfbooking.booking_service.exception;

/** The request is valid but the booking's current state does not allow it (409). */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
