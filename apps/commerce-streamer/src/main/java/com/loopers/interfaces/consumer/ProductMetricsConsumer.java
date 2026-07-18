package com.loopers.interfaces.consumer;

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
import com.loopers.application.metrics.ProductMetricsAggregator;
import com.loopers.confg.kafka.KafkaConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String GROUP_ID = "product-metrics";

    private final ProductMetricsAggregator productMetricsAggregator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {CATALOG_EVENTS_TOPIC, ORDER_EVENTS_TOPIC},
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) throws JsonProcessingException {
        for (ConsumerRecord<Object, Object> message : messages) {
            JsonNode envelope = objectMapper.readTree(String.valueOf(message.value()));
            Optional<ProductActivityEvent> event = ProductActivityEventParser.parse(envelope);
            event.ifPresent(productMetricsAggregator::aggregate);
        }

        acknowledgment.acknowledge();
    }
}
