package com.turfbooking.auth_service.messaging;

import com.turfbooking.auth_service.config.TestMessaging;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.turfbooking.auth_service.event.OwnerRegisteredEvent;

class OwnerEventPublisherTest {

    RabbitTemplate template = mock(RabbitTemplate.class);
    OwnerEventPublisher publisher = new OwnerEventPublisher(template, TestMessaging.defaults(), Duration.ofMillis(200));
    OwnerRegisteredEvent event = new OwnerRegisteredEvent(42L, "Arena", "+919876543210");

    // Simulates what the broker does after receiving the message.
    private void brokerReplies(Consumer<CorrelationData> reply) {
        doAnswer(inv -> {
            reply.accept(inv.getArgument(3));
            return null;
        }).when(template).convertAndSend(eq("auth.events"), eq("owner.registered"), eq((Object) event), any(CorrelationData.class));
    }

    @Test
    void succeedsWhenBrokerAcksAndMessageIsRouted() {
        brokerReplies(cd -> cd.getFuture().complete(new CorrelationData.Confirm(true, null)));
        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    void failsWhenBrokerNacks() {
        brokerReplies(cd -> cd.getFuture().complete(new CorrelationData.Confirm(false, "disk full")));
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(OwnerEventPublishException.class);
    }

    @Test
    void failsWhenNoQueueReceivedTheMessage() {
        brokerReplies(cd -> {
            cd.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "auth.events", "owner.registered"));
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
        });
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(OwnerEventPublishException.class)
                .hasMessageContaining("No queue");
    }

    @Test
    void failsWhenBrokerIsUnreachable() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(template).convertAndSend(any(String.class), any(String.class), any(Object.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(OwnerEventPublishException.class);
    }

    @Test
    void failsWhenBrokerNeverAnswers() {
        brokerReplies(cd -> { });
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(OwnerEventPublishException.class);
    }
}
