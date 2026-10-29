package com.turfbooking.booking_service.outbox;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A message waiting to be sent. Written in the same transaction as the state change it describes. */
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sent as the AMQP message id, so a consumer can recognise a redelivery of the same message.
    @Column(nullable = false, updatable = false, unique = true, length = 36)
    private String messageId = java.util.UUID.randomUUID().toString();

    // What the message is about (e.g. a booking id), so related pending messages can be found.
    @Column(length = 100)
    private String aggregateId;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Null until a queue has accepted the message.
    private Instant publishedAt;
}
