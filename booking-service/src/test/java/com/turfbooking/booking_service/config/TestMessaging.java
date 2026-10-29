package com.turfbooking.booking_service.config;

/** The RabbitMQ names the services ship with, for tests that construct classes directly. */
public final class TestMessaging {

    private TestMessaging() {
    }

    public static MessagingProperties defaults() {
        return new MessagingProperties(
                "booking.commands",
                "turf.events",
                "booking-service.turf-replies",
                "booking-service.turf-replies.dlq",
                "slots.reserve",
                "slots.confirm",
                "slots.release",
                "slots.reserved",
                "slots.rejected",
                "slots.released");
    }
}
