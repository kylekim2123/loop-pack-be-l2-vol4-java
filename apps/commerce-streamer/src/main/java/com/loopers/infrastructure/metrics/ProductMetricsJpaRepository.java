package com.loopers.infrastructure.metrics;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.loopers.domain.metrics.ProductMetricsModel;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    Optional<ProductMetricsModel> findByProductId(Long productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, last_event_at, created_at, updated_at)
        VALUES (:productId, GREATEST(0, :likeDelta), :salesDelta, :viewDelta, :occurredAt, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            like_count = GREATEST(0, like_count + :likeDelta),
            sales_count = sales_count + :salesDelta,
            view_count = view_count + :viewDelta,
            last_event_at = GREATEST(last_event_at, :occurredAt),
            updated_at = NOW(6)
        """, nativeQuery = true)
    void applyDelta(
        Long productId,
        long likeDelta,
        long salesDelta,
        long viewDelta,
        ZonedDateTime occurredAt
    );
}
