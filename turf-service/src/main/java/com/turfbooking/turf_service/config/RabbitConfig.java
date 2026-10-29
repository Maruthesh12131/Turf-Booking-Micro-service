package com.turfbooking.turf_service.config;

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

// turf-service owns the queue it consumes from, so events are kept even while this service is down.
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Bean
    public TopicExchange ownerEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.ownerEventsExchange(), true, false);
    }

    // Messages that fail permanently go to the DLQ (via the default exchange) instead of being retried forever.
    @Bean
    public Queue ownerVerificationQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.verificationQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.verificationDlq())
                .build();
    }

    @Bean
    public Queue ownerVerificationDeadLetterQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.verificationDlq()).build();
    }

    @Bean
    public Binding ownerVerificationBinding(Queue ownerVerificationQueue, TopicExchange ownerEventsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(ownerVerificationQueue).to(ownerEventsExchange).with(props.verificationChangedKey());
    }

    @Bean
    public TopicExchange bookingCommandsExchange(MessagingProperties props) {
        return new TopicExchange(props.bookingCommandsExchange(), true, false);
    }

    @Bean
    public TopicExchange turfEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.turfEventsExchange(), true, false);
    }

    // One queue (and one consumer) for all commands keeps a booking's reserve, confirm and release in the order sent.
    @Bean
    public Queue bookingCommandQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.commandQueue()).deadLetterExchange("").deadLetterRoutingKey(props.commandDlq()).build();
    }

    @Bean
    public Queue bookingCommandDeadLetterQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.commandDlq()).build();
    }

    @Bean
    public Binding reserveBinding(Queue bookingCommandQueue, TopicExchange bookingCommandsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(bookingCommandQueue).to(bookingCommandsExchange).with(props.reserveKey());
    }

    @Bean
    public Binding confirmBinding(Queue bookingCommandQueue, TopicExchange bookingCommandsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(bookingCommandQueue).to(bookingCommandsExchange).with(props.confirmKey());
    }

    @Bean
    public Binding releaseBinding(Queue bookingCommandQueue, TopicExchange bookingCommandsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(bookingCommandQueue).to(bookingCommandsExchange).with(props.releaseKey());
    }

    // Deserialize into the listener's parameter type, ignoring the sender's Java class name in the message headers.
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
