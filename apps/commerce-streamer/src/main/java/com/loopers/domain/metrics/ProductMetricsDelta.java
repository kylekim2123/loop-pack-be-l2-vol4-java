package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

public record ProductMetricsDelta(Long productId, long likeDelta, long salesDelta, long viewDelta, ZonedDateTime occurredAt) {

    private static final long SINGLE_EVENT_INCREMENT = 1L;

    public ProductMetricsDelta {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("발생 시각은 필수입니다.");
        }
    }

    public static ProductMetricsDelta forViewed(Long productId, ZonedDateTime occurredAt) {
        return new ProductMetricsDelta(productId, 0, 0, SINGLE_EVENT_INCREMENT, occurredAt);
    }

    public static ProductMetricsDelta forLikeCreated(Long productId, ZonedDateTime occurredAt) {
        return new ProductMetricsDelta(productId, SINGLE_EVENT_INCREMENT, 0, 0, occurredAt);
    }

    public static ProductMetricsDelta forLikeDeleted(Long productId, ZonedDateTime occurredAt) {
        return new ProductMetricsDelta(productId, -SINGLE_EVENT_INCREMENT, 0, 0, occurredAt);
    }

    public static ProductMetricsDelta forOrderItem(Long productId, long quantity, ZonedDateTime occurredAt) {
        return new ProductMetricsDelta(productId, 0, quantity, 0, occurredAt);
    }
}
