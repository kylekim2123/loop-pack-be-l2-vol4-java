package com.loopers.application.ranking;

import org.springframework.data.domain.Page;

import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.support.ranking.RankingPeriod;

public record RankingResult(RankingPeriodType period, RankingPeriod window, Page<RankingItemInfo> rankings) {
}
