package com.loopers.domain.metrics;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface ProductMetricsRepository {

    void applyDelta(Long productId, long likeDelta, long salesDelta, long viewDelta, ZonedDateTime occurredAt);

    Optional<ProductMetricsModel> findByProductId(Long productId);
}
