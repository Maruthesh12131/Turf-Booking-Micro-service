package com.turfbooking.auth_service.config;

/** The RabbitMQ names the services ship with, for tests that construct classes directly. */
public final class TestMessaging {

    private TestMessaging() {
    }

    public static MessagingProperties defaults() {
        return new MessagingProperties(
                "auth.events",
                "owner.registered");
    }
}
