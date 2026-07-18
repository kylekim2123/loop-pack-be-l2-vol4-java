package com.loopers.application.metrics;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.application.event.LikeDeletedEvent;
import com.loopers.application.event.OrderCreatedEvent;
import com.loopers.application.event.ProductActivityEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsDailyDelta;
import com.loopers.domain.metrics.ProductMetricsDailyRepository;
import com.loopers.domain.metrics.ProductMetricsDelta;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMetricsAggregator {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;

    @Transactional
    public void aggregate(ProductActivityEvent event) {
        if (eventHandledRepository.existsByEventId(event.eventId())) {
            return;
        }
        eventHandledRepository.save(EventHandledModel.from(event.eventId()));

        LocalDate metricDate = event.occurredAt().withZoneSameInstant(SEOUL_ZONE).toLocalDate();

        switch (event) {
            case LikeCreatedEvent likeCreated -> {
                productMetricsRepository.applyDelta(ProductMetricsDelta.forLikeCreated(likeCreated.productId(), likeCreated.occurredAt()));
                productMetricsDailyRepository.applyDelta(ProductMetricsDailyDelta.forLikeCreated(likeCreated.productId(), metricDate));
            }
            case LikeDeletedEvent likeDeleted -> {
                productMetricsRepository.applyDelta(ProductMetricsDelta.forLikeDeleted(likeDeleted.productId(), likeDeleted.occurredAt()));
                productMetricsDailyRepository.applyDelta(ProductMetricsDailyDelta.forLikeDeleted(likeDeleted.productId(), metricDate));
            }
            case ProductViewedEvent productViewed -> {
                productMetricsRepository.applyDelta(ProductMetricsDelta.forViewed(productViewed.productId(), productViewed.occurredAt()));
                productMetricsDailyRepository.applyDelta(ProductMetricsDailyDelta.forViewed(productViewed.productId(), metricDate));
            }
            case OrderCreatedEvent orderCreated -> aggregateOrderItems(orderCreated, metricDate);
        }
    }

    private void aggregateOrderItems(OrderCreatedEvent event, LocalDate metricDate) {
        for (OrderCreatedEvent.Item item : event.items()) {
            productMetricsRepository.applyDelta(ProductMetricsDelta.forOrderItem(item.productId(), item.quantity(), event.occurredAt()));
            productMetricsDailyRepository.applyDelta(
                ProductMetricsDailyDelta.forOrderItem(item.productId(), metricDate, item.quantity(), item.salesAmount()));
        }
    }
}
