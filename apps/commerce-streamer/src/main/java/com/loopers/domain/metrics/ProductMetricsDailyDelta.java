package com.loopers.domain.metrics;

import java.time.LocalDate;

public record ProductMetricsDailyDelta(Long productId, LocalDate metricDate, long likeDelta, long salesDelta, long viewDelta, long salesAmountDelta) {

    private static final long SINGLE_EVENT_INCREMENT = 1L;

    public ProductMetricsDailyDelta {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (metricDate == null) {
            throw new IllegalArgumentException("집계 일자는 필수입니다.");
        }
    }

    public static ProductMetricsDailyDelta forViewed(Long productId, LocalDate metricDate) {
        return new ProductMetricsDailyDelta(productId, metricDate, 0, 0, SINGLE_EVENT_INCREMENT, 0);
    }

    public static ProductMetricsDailyDelta forLikeCreated(Long productId, LocalDate metricDate) {
        return new ProductMetricsDailyDelta(productId, metricDate, SINGLE_EVENT_INCREMENT, 0, 0, 0);
    }

    public static ProductMetricsDailyDelta forLikeDeleted(Long productId, LocalDate metricDate) {
        return new ProductMetricsDailyDelta(productId, metricDate, -SINGLE_EVENT_INCREMENT, 0, 0, 0);
    }

    public static ProductMetricsDailyDelta forOrderItem(Long productId, LocalDate metricDate, long quantity, long salesAmount) {
        return new ProductMetricsDailyDelta(productId, metricDate, 0, quantity, 0, salesAmount);
    }
}
