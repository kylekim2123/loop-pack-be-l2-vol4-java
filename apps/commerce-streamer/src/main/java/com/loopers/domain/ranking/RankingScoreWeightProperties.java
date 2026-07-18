package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "ranking.score.weight")
public record RankingScoreWeightProperties(double view, double like, double order) {
}
