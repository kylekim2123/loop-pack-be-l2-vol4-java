package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<Long> findProductIdsByRank(LocalDate rankingDate, long startIndex, long endIndex);

    long countByRankingDate(LocalDate rankingDate);

    Optional<Long> findRank(LocalDate rankingDate, Long productId);
}
