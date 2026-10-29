package com.turfbooking.booking_service.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ids of messages already handled. A consumer inserts the id in the same transaction as its state change, so a
 * redelivered message (the broker guarantees at-least-once, not exactly-once) is recognised and skipped.
 */
@Entity
@Table(name = "processed_messages")
@NoArgsConstructor
@Getter
@Setter
public class ProcessedMessage {

    @Id
    @Column(length = 100)
    private String messageId;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
    }
}
