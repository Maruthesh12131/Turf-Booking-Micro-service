package com.turfbooking.booking_service.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.turfbooking.booking_service.config.MessagingProperties;
import com.turfbooking.booking_service.event.SlotsRejectedEvent;
import com.turfbooking.booking_service.event.SlotsReleasedEvent;
import com.turfbooking.booking_service.event.SlotsReservedEvent;
import com.turfbooking.booking_service.service.ReservationReplyService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives turf-service's answers. A message is acknowledged only after the database transaction behind it committed;
 * if that fails the broker redelivers it (a few times, then the dead-letter queue). Redelivery is safe because
 * ReservationReplyService records every message id and checks the booking's state under a lock.
 */
@Component
public class TurfReplyListener {

    private final ReservationReplyService replies;
    private final JsonMapper jsonMapper;
    private final MessagingProperties messaging;

    public TurfReplyListener(ReservationReplyService replies, JsonMapper jsonMapper, MessagingProperties messaging) {
        this.replies = replies;
        this.jsonMapper = jsonMapper;
        this.messaging = messaging;
    }

    @RabbitListener(queues = "${app.rabbit.reply-queue}")
    public void onReply(Message message) {
        String key = message.getMessageProperties().getReceivedRoutingKey();
        String messageId = message.getMessageProperties().getMessageId();
        if (messaging.reservedKey().equals(key)) {
            replies.onReserved(read(message, SlotsReservedEvent.class), messageId);
        } else if (messaging.rejectedKey().equals(key)) {
            replies.onRejected(read(message, SlotsRejectedEvent.class), messageId);
        } else if (messaging.releasedKey().equals(key)) {
            replies.onReleased(read(message, SlotsReleasedEvent.class), messageId);
        } else {
            throw new AmqpRejectAndDontRequeueException("Unexpected routing key " + key);
        }
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            T event = jsonMapper.readValue(message.getBody(), type);
            if (event == null) {
                throw new AmqpRejectAndDontRequeueException("Empty event body");
            }
            return event;
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("Unreadable " + type.getSimpleName(), e);
        }
    }
}
