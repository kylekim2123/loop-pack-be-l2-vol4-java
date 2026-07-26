package com.loopers.batch.job.ranking;

public record ScoredProductRank(
    long productId,
    double score,
    long viewCount,
    long likeCount,
    long salesCount,
    long salesAmount
) {
}
