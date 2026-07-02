package com.loopers.infrastructure.metrics;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void applyDelta(Long productId, long likeDelta, long salesDelta, long viewDelta, ZonedDateTime occurredAt) {
        productMetricsJpaRepository.applyDelta(productId, likeDelta, salesDelta, viewDelta, occurredAt);
    }

    @Override
    public Optional<ProductMetricsModel> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId);
    }
}
