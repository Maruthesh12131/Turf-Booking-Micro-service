package com.turfbooking.auth_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// auth-service only declares the exchange; owner-service owns (declares) the queue it consumes from.
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);
    // this logger is used in the unroutableLogger bean. The purpose of this logger is to log messages that are not routed to any queue. 
    // It is used in the unroutableLogger bean, which is a RabbitTemplateCustomizer that sets a ReturnsCallback on the RabbitTemplate. 
    // The ReturnsCallback logs an error message when a message is returned by the broker because it was not routed to any queue. 
    // This helps in debugging and monitoring the messaging system, as it indicates that there may be a configuration issue or that the intended queue is not available.

    @Bean
    public TopicExchange authEventsExchange(MessagingProperties props) {
        return new TopicExchange(props.authEventsExchange(), true, false);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // With mandatory=true the broker hands back a message no queue accepted, instead of silently dropping it.
    @Bean
    public RabbitTemplateCustomizer unroutableLogger() {
        return (RabbitTemplate template) -> template.setReturnsCallback(returned ->
                log.error("Message was not routed to any queue (exchange={}, routingKey={}, reply={})",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
