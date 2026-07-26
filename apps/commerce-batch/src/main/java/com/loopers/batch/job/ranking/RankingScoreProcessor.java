package com.loopers.batch.job.ranking;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.PeriodRankingScoreCalculator;
import com.loopers.domain.ranking.ProductRankAggregate;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RankingScoreProcessor implements ItemProcessor<ProductPeriodSum, ScoredProductRank> {

    private final PeriodRankingScoreCalculator periodRankingScoreCalculator;

    @Override
    public ScoredProductRank process(ProductPeriodSum productPeriodSum) {
        double score = periodRankingScoreCalculator.score(
            new ProductRankAggregate(productPeriodSum.viewCount(), productPeriodSum.likeCount(), productPeriodSum.salesAmount())
        );

        return new ScoredProductRank(
            productPeriodSum.productId(),
            score,
            productPeriodSum.viewCount(),
            productPeriodSum.likeCount(),
            productPeriodSum.salesCount(),
            productPeriodSum.salesAmount()
        );
    }
}
