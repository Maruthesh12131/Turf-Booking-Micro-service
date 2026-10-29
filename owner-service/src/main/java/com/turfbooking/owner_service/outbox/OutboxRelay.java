package com.turfbooking.owner_service.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends outbox rows to RabbitMQ. A row is marked published only after the broker confirmed it and a queue accepted it,
 * so a crash or broker outage leaves it pending and it is sent again. That makes delivery at-least-once, which is why
 * every consumer is idempotent.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    @Autowired
    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this(outboxRepository, rabbitTemplate, Duration.ofSeconds(5));
    }

    OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : batch) {
            if (!send(event)) {
                // Keep order: nothing after a failed event is sent before it. Earlier ones in this batch stay marked.
                return;
            }
            event.setPublishedAt(Instant.now());
        }
    }

    private boolean send(OutboxEvent event) {
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(String.valueOf(event.getId()));
        try {
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                log.warn("Broker rejected outbox event {}: {}", event.getId(), confirm.reason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.warn("No queue accepted outbox event {} (is the consumer's queue declared?)", event.getId());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            log.warn("Could not publish outbox event {}; will retry: {}", event.getId(), e.toString());
            return false;
        }
    }
}
