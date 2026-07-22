package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "product_rank_staging",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_rank_staging_period_type_period_key_product_id",
        columnNames = {"period_type", "period_key", "product_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankStagingModel extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private RankingPeriodType periodType;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

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
