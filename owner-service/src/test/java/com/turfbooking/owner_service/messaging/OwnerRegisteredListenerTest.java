package com.turfbooking.owner_service.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.turfbooking.owner_service.dto.OwnerRequestDTO;
import com.turfbooking.owner_service.event.OwnerRegisteredEvent;
import com.turfbooking.owner_service.service.OwnerService;

import jakarta.validation.Validation;

class OwnerRegisteredListenerTest {

    OwnerService ownerService = mock(OwnerService.class);
    OwnerRegisteredListener listener = new OwnerRegisteredListener(ownerService,
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validEventCreatesProfile() {
        when(ownerService.createIfAbsent(any(), any())).thenReturn(true);
        listener.onOwnerRegistered(new OwnerRegisteredEvent(42L, "Arena Co", "+919876543210"));
        verify(ownerService).createIfAbsent(42L, new OwnerRequestDTO("Arena Co", "+919876543210"));
    }

    @Test
    void duplicateDeliveryIsHarmless() {
        when(ownerService.createIfAbsent(any(), any())).thenReturn(false);
        assertThatCode(() -> listener.onOwnerRegistered(new OwnerRegisteredEvent(42L, "Arena Co", "+919876543210")))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedEventsAreRejectedWithoutRequeue() {
        for (OwnerRegisteredEvent bad : new OwnerRegisteredEvent[] {
                null,
                new OwnerRegisteredEvent(null, "Arena", "+919876543210"),
                new OwnerRegisteredEvent(42L, "", "+919876543210"),
                new OwnerRegisteredEvent(42L, "Arena", "not-a-phone") }) {
            assertThatThrownBy(() -> listener.onOwnerRegistered(bad)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        }
        verifyNoInteractions(ownerService);
    }
}
