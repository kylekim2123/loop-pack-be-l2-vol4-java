package com.loopers.domain.metrics;

import java.time.LocalDate;

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
    name = "product_metrics_daily",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_metrics_daily_product_id_metric_date",
        columnNames = {"product_id", "metric_date"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsDailyModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Builder
    private ProductMetricsDailyModel(
        Long productId, LocalDate metricDate, long likeCount, long salesCount, long viewCount, long salesAmount
    ) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (metricDate == null) {
            throw new IllegalArgumentException("집계 일자는 필수입니다.");
        }

        this.productId = productId;
        this.metricDate = metricDate;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
        this.salesAmount = salesAmount;
    }
}
