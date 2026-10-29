package com.turfbooking.turf_service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

class OutboxRelayTest {

    OutboxRepository repo = mock(OutboxRepository.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    OutboxRelay relay = new OutboxRelay(repo, rabbit, Duration.ofMillis(100));

    private static OutboxEvent event(long id) {
        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setExchange("turf.events");
        e.setRoutingKey("slots.reserved");
        e.setPayload("{\"ownerId\":" + id + "}");
        return e;
    }

    private void brokerAnswers(boolean ack) {
        doAnswer(inv -> {
            CorrelationData c = inv.getArgument(3);
            c.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void confirmedEventsAreMarkedPublished() {
        OutboxEvent a = event(1), b = event(2);
        when(repo.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(a, b));
        brokerAnswers(true);

        relay.relay();

        assertThat(a.getPublishedAt()).isNotNull();
        assertThat(b.getPublishedAt()).isNotNull();
        verify(rabbit, times(2)).send(eq("turf.events"), eq("slots.reserved"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void nackedEventStaysPendingAndLaterOnesAreNotSent() {
        OutboxEvent a = event(1), b = event(2);
        when(repo.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(a, b));
        brokerAnswers(false);

        relay.relay();

        assertThat(a.getPublishedAt()).isNull();
        assertThat(b.getPublishedAt()).isNull();
        verify(rabbit, times(1)).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void brokerDownLeavesEventPendingWithoutThrowing() {
        OutboxEvent a = event(1);
        when(repo.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(a));
        doThrow(new AmqpConnectException(new RuntimeException("down"))).when(rabbit)
                .send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        relay.relay();

        assertThat(a.getPublishedAt()).isNull();
    }

    @Test
    void noConfirmWithinTimeoutLeavesEventPending() {
        OutboxEvent a = event(1);
        when(repo.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(a));

        relay.relay();

        assertThat(a.getPublishedAt()).isNull();
    }

    @Test
    void unroutableEventStaysPending() {
        OutboxEvent a = event(1);
        when(repo.findUnpublishedForUpdate(any(Pageable.class))).thenReturn(List.of(a));
        doAnswer(inv -> {
            CorrelationData c = inv.getArgument(3);
            c.setReturned(mock(org.springframework.amqp.core.ReturnedMessage.class));
            c.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        relay.relay();

        assertThat(a.getPublishedAt()).isNull();
    }
}
