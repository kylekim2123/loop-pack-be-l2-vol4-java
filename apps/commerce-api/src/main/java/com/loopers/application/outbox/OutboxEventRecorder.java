package com.loopers.application.outbox;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeDeletedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEnvelope;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventType;
import com.loopers.support.config.KafkaTopicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxEventRecorder {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeUtil dateTimeUtil;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordLikeCreated(LikeCreatedEvent event) {
        record(KafkaTopicConfig.CATALOG_EVENTS_TOPIC, OutboxEventType.LIKE_CREATED, event.productId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordLikeDeleted(LikeDeletedEvent event) {
        record(KafkaTopicConfig.CATALOG_EVENTS_TOPIC, OutboxEventType.LIKE_DELETED, event.productId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordOrderCreated(OrderCreatedEvent event) {
        record(KafkaTopicConfig.ORDER_EVENTS_TOPIC, OutboxEventType.ORDER_CREATED, event.orderId(), event);
    }

    private void record(String topic, OutboxEventType eventType, Long aggregateId, Object data) {
        OutboxEnvelope envelope = new OutboxEnvelope(
            UUID.randomUUID().toString(),
            eventType.name(),
            String.valueOf(aggregateId),
            dateTimeUtil.now(),
            data
        );

        outboxEventRepository.save(OutboxEventModel.builder()
            .eventId(envelope.eventId())
            .topic(topic)
            .partitionKey(envelope.aggregateId())
            .eventType(envelope.eventType())
            .payload(toJson(envelope))
            .build());
    }

    private String toJson(OutboxEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }
}
