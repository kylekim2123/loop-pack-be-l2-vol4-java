package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsDailyRepository {

    void applyDelta(ProductMetricsDailyDelta delta);

    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);
}
