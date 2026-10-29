package com.turfbooking.owner_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.turfbooking.owner_service.dto.OwnerRequestDTO;
import com.turfbooking.owner_service.event.OwnerRegisteredEvent;
import com.turfbooking.owner_service.service.OwnerService;

import jakarta.validation.Validator;

@Component
public class OwnerRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(OwnerRegisteredListener.class);

    private final OwnerService ownerService;
    private final Validator validator;

    public OwnerRegisteredListener(OwnerService ownerService, Validator validator) {
        this.ownerService = ownerService;
        this.validator = validator;
    }

    @RabbitListener(queues = "${app.rabbit.owner-registered-queue}")
    public void onOwnerRegistered(OwnerRegisteredEvent event) {
        OwnerRequestDTO profile = event == null || event.userId() == null ? null
                : new OwnerRequestDTO(event.businessName(), event.phoneNumber());
        // A malformed event can never succeed, so send it to the DLQ rather than retrying it.
        if (profile == null || !validator.validate(profile).isEmpty()) {
            throw new AmqpRejectAndDontRequeueException("Invalid OwnerRegisteredEvent for userId="
                    + (event == null ? null : event.userId()));
        }
        // Idempotent: the broker may deliver the same event more than once.
        if (ownerService.createIfAbsent(event.userId(), profile)) {
            log.info("Created owner profile for userId={}", event.userId());
        } else {
            log.info("Owner profile already exists for userId={}; event ignored", event.userId());
        }
    }
}
