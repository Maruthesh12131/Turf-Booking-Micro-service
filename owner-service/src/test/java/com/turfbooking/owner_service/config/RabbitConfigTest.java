package com.turfbooking.owner_service.config;

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
            assertThat(names(ctx, TopicExchange.class)).containsExactly("auth.events", "owner.events");
            assertThat(names(ctx, Queue.class)).containsExactly("owner-service.owner-registered", "owner-service.owner-registered.dlq");
            assertThat(bindings(ctx)).containsExactly("auth.events -> owner-service.owner-registered [owner.registered]");
            assertThat(ctx.getBean("ownerRegisteredQueue", Queue.class).getArguments())
                    .containsEntry("x-dead-letter-exchange", "").containsEntry("x-dead-letter-routing-key", "owner-service.owner-registered.dlq");
            MessagingProperties p = ctx.getBean(MessagingProperties.class);
            assertThat(p.ownerEventsExchange()).isEqualTo("owner.events");
            assertThat(p.verificationChangedKey()).isEqualTo("owner.verification.changed");
        });
    }

    @Test
    void environmentVariablesOverrideTheNames() {
        runner.withPropertyValues("OWNER_REGISTERED_QUEUE=custom.q", "OWNER_REGISTERED_DLQ=custom.q.dead", "OWNER_REGISTERED_KEY=custom.key")
                .run(ctx -> {
            assertThat(bindings(ctx)).containsExactly("auth.events -> custom.q [custom.key]");
            assertThat(ctx.getBean("ownerRegisteredQueue", Queue.class).getArguments())
                    .containsEntry("x-dead-letter-routing-key", "custom.q.dead");
        });
    }

    @Test
    void listenerResolvesItsQueueFromConfiguration() {
        runner.run(ctx -> {
            var m = com.turfbooking.owner_service.messaging.OwnerRegisteredListener.class.getDeclaredMethods();
            String raw = java.util.Arrays.stream(m)
                    .map(x -> x.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class))
                    .filter(java.util.Objects::nonNull).findFirst().orElseThrow().queues()[0];
            assertThat(ctx.getEnvironment().resolveRequiredPlaceholders(raw))
                    .isEqualTo(ctx.getBean("ownerRegisteredQueue", Queue.class).getName());
        });
    }
}
