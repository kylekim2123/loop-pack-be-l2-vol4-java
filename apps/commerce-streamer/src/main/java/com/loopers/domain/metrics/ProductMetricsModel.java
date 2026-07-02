package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_metrics_product_id", columnNames = "product_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "last_event_at", nullable = false)
    private ZonedDateTime lastEventAt;

    @Builder
    private ProductMetricsModel(Long productId, long likeCount, long salesCount, long viewCount, ZonedDateTime lastEventAt) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (lastEventAt == null) {
            throw new IllegalArgumentException("마지막 이벤트 시각은 필수입니다.");
        }

        this.productId = productId;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
        this.lastEventAt = lastEventAt;
    }
}
