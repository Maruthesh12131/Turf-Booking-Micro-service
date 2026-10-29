package com.turfbooking.api_gateway.exception;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/** Turns "could not reach the service behind this route" into a clear JSON 503/504 instead of a generic 500. */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler({ ResourceAccessException.class, IOException.class })
    public ResponseEntity<Map<String, Object>> upstreamUnavailable(Exception e) {
        boolean timeout = hasCause(e, SocketTimeoutException.class);
        HttpStatus status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        log.warn("Upstream call failed ({}): {}", status.value(), e.getMessage());
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", timeout ? "The service took too long to respond" : "The service is currently unavailable"));
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
