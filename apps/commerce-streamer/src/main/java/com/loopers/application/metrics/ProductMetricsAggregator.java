package com.loopers.application.metrics;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.event.ConsumedEvent;
import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsAggregator {

    private static final String LIKE_CREATED = "LIKE_CREATED";
    private static final String LIKE_DELETED = "LIKE_DELETED";
    private static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String ORDER_CREATED = "ORDER_CREATED";

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void aggregate(ConsumedEvent event) {
        if (eventHandledRepository.existsByEventId(event.eventId())) {
            return;
        }
        eventHandledRepository.save(EventHandledModel.from(event.eventId()));

        switch (event.eventType()) {
            case LIKE_CREATED -> productMetricsRepository.applyDelta(
                event.data().path("productId").asLong(), 1, 0, 0, event.occurredAt());
            case LIKE_DELETED -> productMetricsRepository.applyDelta(
                event.data().path("productId").asLong(), -1, 0, 0, event.occurredAt());
            case PRODUCT_VIEWED -> productMetricsRepository.applyDelta(
                event.data().path("productId").asLong(), 0, 0, 1, event.occurredAt());
            case ORDER_CREATED -> aggregateOrderItems(event);
            default -> log.warn("집계 대상이 아닌 이벤트 타입 - 건너뜀 (eventId={}, eventType={})",
                event.eventId(), event.eventType());
        }
    }

    private void aggregateOrderItems(ConsumedEvent event) {
        for (JsonNode item : event.data().path("items")) {
            productMetricsRepository.applyDelta(
                item.path("productId").asLong(), 0, item.path("quantity").asLong(), 0, event.occurredAt());
        }
    }
}
