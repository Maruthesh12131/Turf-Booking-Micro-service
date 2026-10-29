package com.turfbooking.booking_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// booking-service sends commands on booking.commands and listens to turf-service's answers on turf.events.
// It owns the queue it consumes from, so replies are kept even while this service is down.
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Bean
    public TopicExchange bookingCommandsExchange(MessagingProperties props) {
        return new TopicExchange(props.bookingCommandsExchange(), true, false);
    }

    @Bean
    public TopicExchange turfEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.turfEventsExchange(), true, false);
    }

    // Messages that fail permanently go to the DLQ (via the default exchange) instead of being retried forever.
    @Bean
    public Queue turfReplyQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.replyQueue()).deadLetterExchange("").deadLetterRoutingKey(props.replyDlq()).build();
    }

    @Bean
    public Queue turfReplyDeadLetterQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.replyDlq()).build();
    }

    @Bean
    public Binding reservedBinding(Queue turfReplyQueue, TopicExchange turfEventsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(turfReplyQueue).to(turfEventsExchange).with(props.reservedKey());
    }

    @Bean
    public Binding rejectedBinding(Queue turfReplyQueue, TopicExchange turfEventsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(turfReplyQueue).to(turfEventsExchange).with(props.rejectedKey());
    }

    @Bean
    public Binding releasedBinding(Queue turfReplyQueue, TopicExchange turfEventsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(turfReplyQueue).to(turfEventsExchange).with(props.releasedKey());
    }

    // Deserialize into the listener's parameter type, ignoring the sender's Java class name in the message headers.
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
