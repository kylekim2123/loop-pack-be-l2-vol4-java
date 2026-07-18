package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "ranking.carry-over")
public record RankingCarryOverProperties(double ratio) {
}
