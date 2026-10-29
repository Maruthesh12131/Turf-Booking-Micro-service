package com.turfbooking.owner_service.config;

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

// owner-service owns the queue it consumes from, so events are kept even while this service is down.
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    @Bean
    public TopicExchange authEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.authEventsExchange(), true, false);
    }

    // Published to by the outbox relay; turf-service owns and binds the queue it consumes from.
    @Bean
    public TopicExchange ownerEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.ownerEventsExchange(), true, false);
    }

    // Messages that fail permanently are routed to the DLQ (via the default exchange) instead of being retried forever.
    @Bean
    public Queue ownerRegisteredQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.ownerRegisteredQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.ownerRegisteredDlq())
                .build();
    }

    @Bean
    public Queue ownerRegisteredDeadLetterQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.ownerRegisteredDlq()).build();
    }

    @Bean
    public Binding ownerRegisteredBinding(Queue ownerRegisteredQueue, TopicExchange authEventsExchange,
            MessagingProperties props) {
        return BindingBuilder.bind(ownerRegisteredQueue).to(authEventsExchange).with(props.ownerRegisteredKey());
    }

    // Deserialize into the listener's parameter type, ignoring the sender's Java class name in the message headers.
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
