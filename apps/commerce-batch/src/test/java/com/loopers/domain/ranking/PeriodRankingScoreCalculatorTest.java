package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PeriodRankingScoreCalculatorTest {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private final PeriodRankingScoreCalculator periodRankingScoreCalculator =
        new PeriodRankingScoreCalculator(new RankingScoreWeightProperties(VIEW_WEIGHT, LIKE_WEIGHT, ORDER_WEIGHT));

    @Nested
    @DisplayName("가중치 반영 순서")
    class WeightOrdering {

        @Test
        @DisplayName("판매 금액이 실린 상품은 좋아요만 쌓인 상품보다 높은 점수를 받는다.")
        void productWithSalesBeatsProductWithLikesOnly() {
            // arrange
            ProductRankAggregate productWithSales = new ProductRankAggregate(0L, 0L, 50_000L);
            ProductRankAggregate productWithLikesOnly = new ProductRankAggregate(0L, 10L, 0L);

            // act
            double salesScore = periodRankingScoreCalculator.score(productWithSales);
            double likesScore = periodRankingScoreCalculator.score(productWithLikesOnly);

            // assert
            assertThat(salesScore).isGreaterThan(likesScore);
        }
    }

    @Nested
    @DisplayName("판매 금액 경계")
    class SalesAmountBoundary {

        @Test
        @DisplayName("판매 금액이 0원이면 판매 항목은 점수에 기여하지 않는다.")
        void zeroSalesAmountContributesNothing() {
            // arrange
            ProductRankAggregate aggregate = new ProductRankAggregate(30L, 10L, 0L);

            // act
            double score = periodRankingScoreCalculator.score(aggregate);

            // assert
            assertThat(score).isEqualTo(VIEW_WEIGHT * 30 + LIKE_WEIGHT * 10);
        }
    }

    @Nested
    @DisplayName("전 지표 0")
    class AllMetricsZero {

        @Test
        @DisplayName("모든 지표가 0이면 점수는 0이다.")
        void allZeroMetricsScoreZero() {
            // arrange
            ProductRankAggregate aggregate = new ProductRankAggregate(0L, 0L, 0L);

            // act
            double score = periodRankingScoreCalculator.score(aggregate);

            // assert
            assertThat(score).isZero();
        }
    }
}
