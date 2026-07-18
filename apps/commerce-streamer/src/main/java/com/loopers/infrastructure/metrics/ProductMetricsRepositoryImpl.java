package com.loopers.infrastructure.metrics;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.ProductMetricsDelta;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void applyDelta(ProductMetricsDelta delta) {
        productMetricsJpaRepository.applyDelta(
            delta.productId(),
            delta.likeDelta(),
            delta.salesDelta(),
            delta.viewDelta(),
            delta.occurredAt()
        );
    }

    @Override
    public Optional<ProductMetricsModel> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId);
    }
}
