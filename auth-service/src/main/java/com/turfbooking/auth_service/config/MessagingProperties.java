package com.turfbooking.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * RabbitMQ exchange and routing-key names used by auth-service.
 * Bound from app.rabbit.* (set through environment variables, see .env.example); the names must match across services.
 */
@Validated
@ConfigurationProperties(prefix = "app.rabbit")
public record MessagingProperties(
        @NotBlank String authEventsExchange,
        @NotBlank String ownerRegisteredKey) {
}
