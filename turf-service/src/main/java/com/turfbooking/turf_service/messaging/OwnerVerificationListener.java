package com.turfbooking.turf_service.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.turfbooking.turf_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.turf_service.service.OwnerVisibilityService;

@Component
public class OwnerVerificationListener {

    private final OwnerVisibilityService visibilityService;

    public OwnerVerificationListener(OwnerVisibilityService visibilityService) {
        this.visibilityService = visibilityService;
    }

    @RabbitListener(queues = "${app.rabbit.verification-queue}")
    public void onVerificationChanged(OwnerVerificationChangedEvent event) {
        // A malformed event can never succeed, so send it to the DLQ rather than retrying it.
        if (event == null || event.ownerId() == null || event.status() == null || event.version() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid OwnerVerificationChangedEvent");
        }
        try {
            visibilityService.apply(event);
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
