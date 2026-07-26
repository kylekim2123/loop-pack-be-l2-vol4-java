package com.loopers.support.ranking;

import java.time.LocalDate;

public record RankingPeriod(String periodKey, LocalDate startDate, LocalDate endDate) {
}
