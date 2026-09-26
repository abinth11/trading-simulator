package com.tradingsim.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a Kafka event in the outbox as part of the caller's transaction.
 *
 * The event reaches Kafka only if that transaction commits ({@link OutboxRelay} publishes it
 * afterwards), so consumers never see an event for a change that was rolled back, and never
 * see it before the change is visible in the database.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name}")
    private String producer;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String key, Object payload) {
        jdbcTemplate.update(
                "INSERT INTO outbox_events (producer, topic, event_key, payload) VALUES (?, ?, ?, ?)",
                producer, topic, key, toJson(payload));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox payload " + payload.getClass(), e);
        }
    }
}
