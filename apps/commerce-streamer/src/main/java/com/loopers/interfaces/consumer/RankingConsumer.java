package com.loopers.interfaces.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.ProductActivityEvent;
import com.loopers.application.event.ProductActivityEventParser;
import com.loopers.application.ranking.RankingScoreAggregator;
import com.loopers.confg.kafka.KafkaConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingConsumer {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String GROUP_ID = "ranking";

    private final RankingScoreAggregator rankingScoreAggregator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {CATALOG_EVENTS_TOPIC, ORDER_EVENTS_TOPIC},
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) throws JsonProcessingException {
        List<ProductActivityEvent> events = new ArrayList<>();
        for (ConsumerRecord<Object, Object> message : messages) {
            JsonNode envelope = objectMapper.readTree(String.valueOf(message.value()));
            Optional<ProductActivityEvent> event = ProductActivityEventParser.parse(envelope);
            event.ifPresent(events::add);
        }

        try {
            rankingScoreAggregator.aggregate(events);
        } catch (RuntimeException e) {
            log.error("랭킹 점수 적재 실패 - 배치를 ack하지 않고 재시도 대상으로 남깁니다.", e);
            throw e;
        }

        acknowledgment.acknowledge();
    }
}
