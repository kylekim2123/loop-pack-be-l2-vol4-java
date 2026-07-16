package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    void applyScores(LocalDate rankingDate, List<RankingScoreEvent> events);
}
