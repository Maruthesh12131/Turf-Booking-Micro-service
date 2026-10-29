package com.turfbooking.turf_service.exception;

/** A command arrived that contradicts what is stored (e.g. confirming a reservation that was released). Needs a human. */
public class ReservationStateException extends RuntimeException {
    public ReservationStateException(String message) {
        super(message);
    }
}
