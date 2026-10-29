package com.turfbooking.turf_service.exception;

public class OwnerNotVerifiedException extends RuntimeException {
    public OwnerNotVerifiedException() {
        super("Your owner account must be verified before you can create a turf");
    }
}
