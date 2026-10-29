package com.turfbooking.booking_service.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public OutboxService(OutboxRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    /** Must run inside the caller's transaction: the message exists if and only if the state change commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, Object payload) {
        enqueue(exchange, routingKey, null, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, String aggregateId, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(aggregateId);
        event.setExchange(exchange);
        event.setRoutingKey(routingKey);
        event.setPayload(jsonMapper.writeValueAsString(payload));
        outboxRepository.save(event);
    }
}
