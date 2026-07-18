package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RankingScoreCalculator {

    private final RankingScoreWeightProperties rankingScoreWeightProperties;

    public double productViewedScore() {
        return rankingScoreWeightProperties.view();
    }

    public double likeCreatedScore() {
        return rankingScoreWeightProperties.like();
    }

    public double likeDeletedScore() {
        return -likeCreatedScore();
    }

    public double orderItemScore(long unitPrice, long quantity) {
        return rankingScoreWeightProperties.order() * Math.log1p(unitPrice * quantity);
    }
}
