package com.turfbooking.booking_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * Loads the real application.properties and checks the RabbitMQ topology that RabbitConfig declares from it.
 * Runs without a broker or database.
 */
class RabbitConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RabbitConfig.class);

    private static List<String> bindings(ApplicationContext ctx) {
        return ctx.getBeansOfType(Binding.class).values().stream()
                .map(b -> b.getExchange() + " -> " + b.getDestination() + " [" + b.getRoutingKey() + "]")
                .sorted().collect(Collectors.toList());
    }

    private static List<String> names(ApplicationContext ctx, Class<?> type) {
        return ctx.getBeansOfType(org.springframework.amqp.core.Declarable.class).values().stream()
                .filter(type::isInstance)
                .map(d -> d instanceof Queue q ? q.getName() : ((TopicExchange) d).getName())
                .sorted().collect(Collectors.toList());
    }

    @Test
    void declaresTheCurrentTopologyFromApplicationProperties() {
        runner.run(ctx -> {
            assertThat(names(ctx, TopicExchange.class)).containsExactly("booking.commands", "turf.events");
            assertThat(names(ctx, Queue.class)).containsExactly("booking-service.turf-replies", "booking-service.turf-replies.dlq");
            assertThat(bindings(ctx)).containsExactly(
                    "turf.events -> booking-service.turf-replies [slots.rejected]",
                    "turf.events -> booking-service.turf-replies [slots.released]",
                    "turf.events -> booking-service.turf-replies [slots.reserved]");
            assertThat(ctx.getBean("turfReplyQueue", Queue.class).getArguments())
                    .containsEntry("x-dead-letter-exchange", "").containsEntry("x-dead-letter-routing-key", "booking-service.turf-replies.dlq");
            MessagingProperties p = ctx.getBean(MessagingProperties.class);
            assertThat(p.bookingCommandsExchange()).isEqualTo("booking.commands");
            assertThat(p.reserveKey()).isEqualTo("slots.reserve");
            assertThat(p.confirmKey()).isEqualTo("slots.confirm");
            assertThat(p.releaseKey()).isEqualTo("slots.release");
        });
    }

    @Test
    void environmentVariablesOverrideTheNames() {
        runner.withPropertyValues("REPLY_QUEUE=custom.replies", "REPLY_DLQ=custom.replies.dead", "RESERVED_KEY=custom.reserved")
                .run(ctx -> {
            assertThat(bindings(ctx)).contains("turf.events -> custom.replies [custom.reserved]");
            assertThat(ctx.getBean("turfReplyQueue", Queue.class).getArguments())
                    .containsEntry("x-dead-letter-routing-key", "custom.replies.dead");
        });
    }

    @Test
    void listenerResolvesItsQueueFromConfiguration() {
        runner.run(ctx -> {
            var m = com.turfbooking.booking_service.messaging.TurfReplyListener.class.getDeclaredMethods();
            String raw = java.util.Arrays.stream(m)
                    .map(x -> x.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class))
                    .filter(java.util.Objects::nonNull).findFirst().orElseThrow().queues()[0];
            assertThat(ctx.getEnvironment().resolveRequiredPlaceholders(raw))
                    .isEqualTo(ctx.getBean("turfReplyQueue", Queue.class).getName());
        });
    }
}
