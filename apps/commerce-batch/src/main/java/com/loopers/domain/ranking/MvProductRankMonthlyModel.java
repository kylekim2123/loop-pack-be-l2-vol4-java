package com.loopers.domain.ranking;

import java.time.LocalDate;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mv_product_rank_monthly_period_key_product_id", columnNames = {"period_key", "product_id"}),
        @UniqueConstraint(name = "uk_mv_product_rank_monthly_period_key_rank", columnNames = {"period_key", "`rank`"})
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthlyModel extends BaseEntity {

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "`rank`", nullable = false)
    private int rank;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;
}
