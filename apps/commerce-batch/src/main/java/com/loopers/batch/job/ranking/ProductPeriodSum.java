package com.loopers.batch.job.ranking;

public record ProductPeriodSum(
    long productId,
    long viewCount,
    long likeCount,
    long salesCount,
    long salesAmount
) {
}
