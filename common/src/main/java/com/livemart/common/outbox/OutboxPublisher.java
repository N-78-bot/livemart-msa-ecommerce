package com.livemart.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livemart.common.event.DomainEvent;
import com.livemart.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher implements EventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        publish(event.getAggregateType() + "-events", event);
    }

    @Override
    @Transactional
    public void publish(String topic, DomainEvent event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(event.getAggregateType())
                    .aggregateId(event.getAggregateId())
                    .eventType(event.getEventType())
                    .payload(objectMapper.writeValueAsString(event))
                    .topic(topic)
                    .build();
            outboxRepository.save(outboxEvent);
            log.debug("Outbox event saved: type={}, aggregateId={}", event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            log.error("Failed to save outbox event", e);
            throw new IllegalStateException("Failed to publish event to outbox", e);
        }
    }
}
