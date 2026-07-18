package com.loopers.domain.ranking;

import java.util.List;

public record RankingScoreEvent(String eventId, List<ProductScore> productScores) {

    public record ProductScore(long productId, double score) {
    }
}
