package com.loopers.infrastructure.metrics;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.loopers.domain.metrics.ProductMetricsDailyModel;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDailyModel, Long> {

    Optional<ProductMetricsDailyModel> findByProductIdAndMetricDate(Long productId, LocalDate metricDate);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics_daily (product_id, metric_date, like_count, sales_count, view_count, sales_amount, created_at, updated_at)
        VALUES (:productId, :metricDate, GREATEST(0, :likeDelta), :salesDelta, :viewDelta, :salesAmountDelta, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            like_count = GREATEST(0, like_count + :likeDelta),
            sales_count = sales_count + :salesDelta,
            view_count = view_count + :viewDelta,
            sales_amount = sales_amount + :salesAmountDelta,
            updated_at = NOW(6)
        """, nativeQuery = true)
    void applyDelta(
        Long productId,
        LocalDate metricDate,
        long likeDelta,
        long salesDelta,
        long viewDelta,
        long salesAmountDelta
    );
}
