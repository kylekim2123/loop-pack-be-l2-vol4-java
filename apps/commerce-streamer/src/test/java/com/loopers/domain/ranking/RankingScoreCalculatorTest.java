package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingScoreCalculatorTest {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private final RankingScoreCalculator rankingScoreCalculator =
        new RankingScoreCalculator(new RankingScoreWeightProperties(VIEW_WEIGHT, LIKE_WEIGHT, ORDER_WEIGHT));

    @Nested
    @DisplayName("이벤트 점수 순서")
    class ScoreOrdering {

        @Test
        @DisplayName("주문 1건의 점수는 좋아요 3건의 점수 합보다 크다.")
        void orderScoreExceedsThreeLikesScore() {
            // arrange
            double threeLikesScore = rankingScoreCalculator.likeCreatedScore() * 3;

            // act
            double orderScore = rankingScoreCalculator.orderItemScore(50_000L, 1L);

            // assert
            assertThat(orderScore).isGreaterThan(threeLikesScore);
        }

        @Test
        @DisplayName("조회 점수 < 좋아요 점수 < 주문 점수 순서를 따른다.")
        void followsIntendedScoreOrder() {
            // arrange & act
            double productViewedScore = rankingScoreCalculator.productViewedScore();
            double likeCreatedScore = rankingScoreCalculator.likeCreatedScore();
            double orderItemScore = rankingScoreCalculator.orderItemScore(50_000L, 1L);

            // assert
            assertAll(
                () -> assertThat(productViewedScore).isLessThan(likeCreatedScore),
                () -> assertThat(likeCreatedScore).isLessThan(orderItemScore)
            );
        }
    }

    @Nested
    @DisplayName("주문 점수 경계")
    class OrderAmountBoundary {

        @Test
        @DisplayName("주문 금액이 0원이면 주문 점수는 0이다.")
        void ordersWithZeroAmountScoreZero() {
            // arrange & act
            double orderScore = rankingScoreCalculator.orderItemScore(0L, 1L);

            // assert
            assertThat(orderScore).isZero();
        }
    }

    @Nested
    @DisplayName("좋아요 점수 대칭")
    class LikeSymmetry {

        @Test
        @DisplayName("좋아요 취소 점수는 좋아요 생성 점수의 정확한 음수다.")
        void likeDeletedScoreIsExactNegationOfLikeCreatedScore() {
            // arrange & act
            double likeCreatedScore = rankingScoreCalculator.likeCreatedScore();
            double likeDeletedScore = rankingScoreCalculator.likeDeletedScore();

            // assert
            assertThat(likeDeletedScore).isEqualTo(-likeCreatedScore);
        }
    }
}
