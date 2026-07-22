package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<Long> findDailyProductIdsByRank(LocalDate rankingDate, long startIndex, long endIndex);

    long countDailyRanking(LocalDate rankingDate);

    Optional<Long> findDailyRank(LocalDate rankingDate, Long productId);

    List<RankedProduct> findWeeklyOrMonthlyRankedProducts(RankingPeriodType periodType, String periodKey, int page, int size);

    long countWeeklyOrMonthlyRankedProducts(RankingPeriodType periodType, String periodKey);
}
