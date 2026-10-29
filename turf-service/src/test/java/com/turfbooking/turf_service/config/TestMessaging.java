package com.turfbooking.turf_service.config;

/** The RabbitMQ names the services ship with, for tests that construct classes directly. */
public final class TestMessaging {

    private TestMessaging() {
    }

    public static MessagingProperties defaults() {
        return new MessagingProperties(
                "owner.events",
                "owner.verification.changed",
                "turf-service.owner-verification",
                "turf-service.owner-verification.dlq",
                "booking.commands",
                "turf.events",
                "turf-service.booking-commands",
                "turf-service.booking-commands.dlq",
                "slots.reserve",
                "slots.confirm",
                "slots.release",
                "slots.reserved",
                "slots.rejected",
                "slots.released");
    }
}
