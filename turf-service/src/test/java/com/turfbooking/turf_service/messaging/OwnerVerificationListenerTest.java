package com.turfbooking.turf_service.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.turfbooking.turf_service.event.OwnerVerificationChangedEvent;
import com.turfbooking.turf_service.service.OwnerVisibilityService;

class OwnerVerificationListenerTest {

    OwnerVisibilityService service = mock(OwnerVisibilityService.class);
    OwnerVerificationListener listener = new OwnerVerificationListener(service);

    @Test
    void validEventIsApplied() {
        OwnerVerificationChangedEvent e = new OwnerVerificationChangedEvent(5L, "VERIFIED", 1L);
        listener.onVerificationChanged(e);
        verify(service).apply(e);
    }

    @Test
    void malformedEventGoesToDeadLetterQueueWithoutRetry() {
        assertThatThrownBy(() -> listener.onVerificationChanged(new OwnerVerificationChangedEvent(null, "VERIFIED", 1L)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onVerificationChanged(null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedStatusGoesToDeadLetterQueue() {
        OwnerVerificationChangedEvent e = new OwnerVerificationChangedEvent(5L, "PENDING", 1L);
        doThrow(new IllegalArgumentException("Unsupported")).when(service).apply(e);
        assertThatThrownBy(() -> listener.onVerificationChanged(e)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
