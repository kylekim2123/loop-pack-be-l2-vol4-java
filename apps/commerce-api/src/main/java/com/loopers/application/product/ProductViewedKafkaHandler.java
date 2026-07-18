package com.loopers.application.product;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEnvelope;
import com.loopers.domain.outbox.OutboxEventType;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.infrastructure.kafka.KafkaMessagePublisher;
import com.loopers.support.config.AsyncConfig;
import com.loopers.support.config.KafkaTopicConfig;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductViewedKafkaHandler {

    private final KafkaMessagePublisher kafkaMessagePublisher;
    private final ObjectMapper objectMapper;
    private final DateTimeUtil dateTimeUtil;

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishProductViewed(ProductViewedEvent event) {
        OutboxEnvelope envelope = new OutboxEnvelope(
            UUID.randomUUID().toString(),
            OutboxEventType.PRODUCT_VIEWED.name(),
            String.valueOf(event.productId()),
            dateTimeUtil.now(),
            event
        );

        kafkaMessagePublisher.publish(KafkaTopicConfig.CATALOG_EVENTS_TOPIC, envelope.aggregateId(), objectMapper.valueToTree(envelope));
    }
}
