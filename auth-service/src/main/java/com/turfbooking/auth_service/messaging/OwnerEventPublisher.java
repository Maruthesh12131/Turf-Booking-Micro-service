package com.turfbooking.auth_service.messaging;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.turfbooking.auth_service.config.MessagingProperties;
import com.turfbooking.auth_service.event.OwnerRegisteredEvent;

@Component
public class OwnerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OwnerEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messaging;
    private final Duration confirmTimeout;

    @Autowired
    public OwnerEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties messaging) {
        this(rabbitTemplate, messaging, Duration.ofSeconds(5));
    }

    OwnerEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties messaging, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.messaging = messaging;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * Sends the event and waits until the broker confirms it was accepted and routed to a queue.
     * Called inside the signup transaction, so any failure here rolls the new user back.
     *
     * @throws OwnerEventPublishException if the broker is unreachable, rejects the message, or no queue received it
     */
    public void publish(OwnerRegisteredEvent event) {
        CorrelationData correlation = new CorrelationData();
        try {
            rabbitTemplate.convertAndSend(messaging.authEventsExchange(), messaging.ownerRegisteredKey(), event, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new OwnerEventPublishException("Broker rejected the message: " + confirm.reason(), null);
            }
            if (correlation.getReturned() != null) {
                throw new OwnerEventPublishException("No queue received the message (is owner-service's queue declared?)", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail(event, e);
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            throw fail(event, e);
        } catch (OwnerEventPublishException e) {
            log.error("Could not publish OwnerRegisteredEvent for userId={}: {}", event.userId(), e.getMessage());
            throw e;
        }
    }

    private OwnerEventPublishException fail(OwnerRegisteredEvent event, Exception cause) {
        log.error("Could not publish OwnerRegisteredEvent for userId={}", event.userId(), cause);
        return new OwnerEventPublishException("Could not publish OwnerRegisteredEvent", cause);
    }
}
