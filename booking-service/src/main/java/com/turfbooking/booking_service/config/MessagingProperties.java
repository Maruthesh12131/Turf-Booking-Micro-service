package com.turfbooking.booking_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * RabbitMQ exchange, queue and routing-key names used by booking-service.
 * Bound from app.rabbit.* (set through environment variables, see .env.example); the names must match across services.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbit")
public record MessagingProperties(
        @NotBlank String bookingCommandsExchange,
        @NotBlank String turfEventsExchange,
        @NotBlank String replyQueue,
        @NotBlank String replyDlq,
        @NotBlank String reserveKey,
        @NotBlank String confirmKey,
        @NotBlank String releaseKey,
        @NotBlank String reservedKey,
        @NotBlank String rejectedKey,
        @NotBlank String releasedKey) {
}
