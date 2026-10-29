package com.turfbooking.turf_service.exception;

public class SlotsAlreadyExistException extends RuntimeException {

    public SlotsAlreadyExistException(String message) {
        super(message);
    }
}
