package com.loopers.infrastructure.metrics;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.metrics.ProductMetricsDailyDelta;
import com.loopers.domain.metrics.ProductMetricsDailyModel;
import com.loopers.domain.metrics.ProductMetricsDailyRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMetricsDailyRepositoryImpl implements ProductMetricsDailyRepository {

    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Override
    public void applyDelta(ProductMetricsDailyDelta delta) {
        productMetricsDailyJpaRepository.applyDelta(
            delta.productId(),
            delta.metricDate(),
            delta.likeDelta(),
            delta.salesDelta(),
            delta.viewDelta(),
            delta.salesAmountDelta()
        );
    }

    @Override
    public Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate) {
        return productMetricsDailyJpaRepository.findByProductIdAndMetricDate(productId, metricDate);
    }
}
