package com.turfbooking.turf_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * RabbitMQ exchange, queue and routing-key names used by turf-service.
 * Bound from app.rabbit.* (set through environment variables, see .env.example); the names must match across services.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbit")
public record MessagingProperties(
        @NotBlank String ownerEventsExchange,
        @NotBlank String verificationChangedKey,
        @NotBlank String verificationQueue,
        @NotBlank String verificationDlq,
        @NotBlank String bookingCommandsExchange,
        @NotBlank String turfEventsExchange,
        @NotBlank String commandQueue,
        @NotBlank String commandDlq,
        @NotBlank String reserveKey,
        @NotBlank String confirmKey,
        @NotBlank String releaseKey,
        @NotBlank String reservedKey,
        @NotBlank String rejectedKey,
        @NotBlank String releasedKey) {
}
