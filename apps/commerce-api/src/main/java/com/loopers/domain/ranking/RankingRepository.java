package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    List<Long> findProductIdsByRank(LocalDate rankingDate, long startIndex, long endIndex);

    long countByRankingDate(LocalDate rankingDate);
}
