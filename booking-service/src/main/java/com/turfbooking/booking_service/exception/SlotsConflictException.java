package com.turfbooking.booking_service.exception;

public class SlotsConflictException extends RuntimeException {
    public SlotsConflictException() {
        super("One or more of the selected slots are already being booked");
    }
}
