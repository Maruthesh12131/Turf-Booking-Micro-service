package com.turfbooking.turf_service.config;

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
            assertThat(names(ctx, TopicExchange.class)).containsExactly("booking.commands", "owner.events", "turf.events");
            assertThat(names(ctx, Queue.class)).containsExactly("turf-service.booking-commands",
                    "turf-service.booking-commands.dlq", "turf-service.owner-verification",
                    "turf-service.owner-verification.dlq");
            assertThat(bindings(ctx)).containsExactly(
                    "booking.commands -> turf-service.booking-commands [slots.confirm]",
                    "booking.commands -> turf-service.booking-commands [slots.release]",
                    "booking.commands -> turf-service.booking-commands [slots.reserve]",
                    "owner.events -> turf-service.owner-verification [owner.verification.changed]");
            Map<String, Object> args = ctx.getBean("bookingCommandQueue", Queue.class).getArguments();
            assertThat(args).containsEntry("x-dead-letter-exchange", "").containsEntry("x-dead-letter-routing-key", "turf-service.booking-commands.dlq");
            args = ctx.getBean("ownerVerificationQueue", Queue.class).getArguments();
            assertThat(args).containsEntry("x-dead-letter-routing-key", "turf-service.owner-verification.dlq");
            MessagingProperties p = ctx.getBean(MessagingProperties.class);
            assertThat(p.reservedKey()).isEqualTo("slots.reserved");
            assertThat(p.rejectedKey()).isEqualTo("slots.rejected");
            assertThat(p.releasedKey()).isEqualTo("slots.released");
        });
    }

    @Test
    void environmentVariablesOverrideTheNames() {
        runner.withPropertyValues("COMMAND_QUEUE=custom.commands", "COMMAND_DLQ=custom.commands.dead",
                "RESERVE_KEY=custom.reserve").run(ctx -> {
            assertThat(bindings(ctx)).contains("booking.commands -> custom.commands [custom.reserve]");
            assertThat(ctx.getBean("bookingCommandQueue", Queue.class).getArguments())
                    .containsEntry("x-dead-letter-routing-key", "custom.commands.dead");
        });
    }

    @Test
    void listenersResolveTheirQueueFromConfiguration() throws Exception {
        runner.run(ctx -> {
            var env = ctx.getEnvironment();
            assertThat(listenerQueue(com.turfbooking.turf_service.messaging.BookingCommandListener.class, env))
                    .isEqualTo(ctx.getBean("bookingCommandQueue", Queue.class).getName());
            assertThat(listenerQueue(com.turfbooking.turf_service.messaging.OwnerVerificationListener.class, env))
                    .isEqualTo(ctx.getBean("ownerVerificationQueue", Queue.class).getName());
        });
    }

    static String listenerQueue(Class<?> type, org.springframework.core.env.Environment env) {
        for (var m : type.getDeclaredMethods()) {
            var a = m.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
            if (a != null) {
                return env.resolveRequiredPlaceholders(a.queues()[0]);
            }
        }
        throw new AssertionError("no @RabbitListener on " + type);
    }
}
