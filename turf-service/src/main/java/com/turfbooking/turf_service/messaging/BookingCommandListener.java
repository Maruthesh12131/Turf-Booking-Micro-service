package com.turfbooking.turf_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.turfbooking.turf_service.config.MessagingProperties;
import com.turfbooking.turf_service.event.ConfirmReservationCommand;
import com.turfbooking.turf_service.event.ReleaseSlotsCommand;
import com.turfbooking.turf_service.event.ReserveSlotsCommand;
import com.turfbooking.turf_service.exception.ReservationStateException;
import com.turfbooking.turf_service.service.SlotReservationService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives booking-service's commands. The message is acknowledged only after the database transaction behind it has
 * committed; if that fails the broker redelivers it (a few times, then it goes to the dead-letter queue). Redelivery is
 * safe because SlotReservationService is idempotent per reservation id.
 */
@Component
public class BookingCommandListener {

    private static final Logger log = LoggerFactory.getLogger(BookingCommandListener.class);

    private final SlotReservationService reservationService;
    private final JsonMapper jsonMapper;
    private final MessagingProperties messaging;

    public BookingCommandListener(SlotReservationService reservationService, JsonMapper jsonMapper,
            MessagingProperties messaging) {
        this.reservationService = reservationService;
        this.jsonMapper = jsonMapper;
        this.messaging = messaging;
    }

    @RabbitListener(queues = "${app.rabbit.command-queue}")
    public void onCommand(Message message) {
        String key = message.getMessageProperties().getReceivedRoutingKey();
        try {
            if (messaging.reserveKey().equals(key)) {
                reservationService.reserve(read(message, ReserveSlotsCommand.class));
            } else if (messaging.confirmKey().equals(key)) {
                reservationService.confirm(read(message, ConfirmReservationCommand.class));
            } else if (messaging.releaseKey().equals(key)) {
                reservationService.release(read(message, ReleaseSlotsCommand.class));
            } else {
                throw new AmqpRejectAndDontRequeueException("Unexpected routing key " + key);
            }
        } catch (ReservationStateException e) {
            // Retrying cannot fix a contradiction; park it in the dead-letter queue for a person to look at.
            log.error("Contradictory command ({}): {}", key, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }

    private <T> T read(Message message, Class<T> type) {
        try {
            T command = jsonMapper.readValue(message.getBody(), type);
            if (command == null) {
                throw new AmqpRejectAndDontRequeueException("Empty command body");
            }
            return command;
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("Unreadable " + type.getSimpleName(), e);
        }
    }
}
