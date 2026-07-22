package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "mv_product_rank_weekly")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeekly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "`rank`", nullable = false)
    private int rank;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
