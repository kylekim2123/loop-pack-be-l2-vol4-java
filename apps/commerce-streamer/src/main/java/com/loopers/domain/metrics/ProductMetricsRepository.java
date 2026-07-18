package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    void applyDelta(ProductMetricsDelta delta);

    Optional<ProductMetricsModel> findByProductId(Long productId);
}
