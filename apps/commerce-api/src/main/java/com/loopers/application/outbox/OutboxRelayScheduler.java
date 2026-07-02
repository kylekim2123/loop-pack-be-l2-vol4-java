package com.loopers.application.outbox;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.infrastructure.kafka.KafkaMessagePublisher;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final long POLL_INTERVAL_MILLIS = 1_000L;
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaMessagePublisher kafkaMessagePublisher;
    private final ObjectMapper objectMapper;
    private final DateTimeUtil dateTimeUtil;

    @Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
    public void relay() {
        List<OutboxEventModel> unpublishedEvents = outboxEventRepository.findUnpublished(BATCH_SIZE);

        for (OutboxEventModel outboxEvent : unpublishedEvents) {
            try {
                kafkaMessagePublisher.publish(
                    outboxEvent.getTopic(),
                    outboxEvent.getPartitionKey(),
                    objectMapper.readTree(outboxEvent.getPayload())
                );
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("Outbox 발행 실패 - 다음 주기에 재시도 (eventId={}, eventType={})",
                    outboxEvent.getEventId(), outboxEvent.getEventType(), e);
                break;
            }

            outboxEvent.markPublished(dateTimeUtil.now());
            outboxEventRepository.save(outboxEvent);
        }
    }
}
