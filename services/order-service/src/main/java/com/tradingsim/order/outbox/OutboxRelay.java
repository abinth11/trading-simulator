package com.tradingsim.order.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes this service's committed outbox rows to Kafka, oldest first, and deletes them.
 *
 * Delivery is at-least-once: a crash between the Kafka ack and the delete re-sends the event,
 * so consumers must tolerate duplicates. A failed send stops the batch and is retried on the
 * next poll, which keeps events in order.
 */
@Component
@Slf4j
public class OutboxRelay implements DisposableBean {

    private static final int BATCH_SIZE = 200;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DefaultKafkaProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.application.name}")
    private String producer;

    public OutboxRelay(JdbcTemplate jdbcTemplate,
                       TransactionTemplate transactionTemplate,
                       ProducerFactory<?, ?> bootProducerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        // Payloads are already JSON, so send them as-is with the app's producer settings
        this.producerFactory = new DefaultKafkaProducerFactory<>(
                bootProducerFactory.getConfigurationProperties(), new StringSerializer(), new StringSerializer());
        this.kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:50}")
    public void relay() {
        Integer published;
        do {
            published = transactionTemplate.execute(status -> publishBatch());
        } while (published != null && published == BATCH_SIZE);
    }

    private int publishBatch() {
        // SKIP LOCKED: a second instance of this service would take different rows instead of waiting
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, topic, event_key, payload
                FROM outbox_events
                WHERE producer = ?
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, i) -> new OutboxRow(rs.getLong("id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("payload")),
                producer, BATCH_SIZE);

        int published = 0;
        for (OutboxRow row : rows) {
            try {
                kafkaTemplate.send(row.topic(), row.key(), row.payload()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Outbox publish failed for event {} to {} — will retry: {}", row.id(), row.topic(), e.getMessage());
                break;
            }
            jdbcTemplate.update("DELETE FROM outbox_events WHERE id = ?", row.id());
            published++;
        }
        return published;
    }

    @Override
    public void destroy() {
        producerFactory.destroy();
    }

    private record OutboxRow(long id, String topic, String key, String payload) {}
}
