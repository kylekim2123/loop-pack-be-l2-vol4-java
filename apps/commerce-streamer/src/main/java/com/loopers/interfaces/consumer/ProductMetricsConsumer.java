package com.loopers.interfaces.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ConsumedEvent;
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
            ConsumedEvent event = ConsumedEvent.from(objectMapper.readTree(String.valueOf(message.value())));
            productMetricsAggregator.aggregate(event);
        }

        acknowledgment.acknowledge();
    }
}
