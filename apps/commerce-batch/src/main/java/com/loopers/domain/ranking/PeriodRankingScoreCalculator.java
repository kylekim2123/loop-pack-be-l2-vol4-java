package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PeriodRankingScoreCalculator {

    private final RankingScoreWeightProperties rankingScoreWeightProperties;

    public double score(ProductRankAggregate productRankAggregate) {
        return rankingScoreWeightProperties.view() * productRankAggregate.viewCount()
            + rankingScoreWeightProperties.like() * productRankAggregate.likeCount()
            + rankingScoreWeightProperties.order() * Math.log1p(productRankAggregate.salesAmount());
    }
}
