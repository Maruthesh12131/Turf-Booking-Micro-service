package com.turfbooking.auth_service.config;

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
    void declaresOnlyTheAuthEventsExchange() {
        runner.run(ctx -> {
            assertThat(names(ctx, TopicExchange.class)).containsExactly("auth.events");
            assertThat(ctx.getBean(MessagingProperties.class).ownerRegisteredKey()).isEqualTo("owner.registered");
        });
    }

    @Test
    void environmentVariablesOverrideTheNames() {
        runner.withPropertyValues("AUTH_EVENTS_EXCHANGE=custom.auth", "OWNER_REGISTERED_KEY=custom.key").run(ctx -> {
            assertThat(names(ctx, TopicExchange.class)).containsExactly("custom.auth");
            assertThat(ctx.getBean(MessagingProperties.class).ownerRegisteredKey()).isEqualTo("custom.key");
        });
    }
}
