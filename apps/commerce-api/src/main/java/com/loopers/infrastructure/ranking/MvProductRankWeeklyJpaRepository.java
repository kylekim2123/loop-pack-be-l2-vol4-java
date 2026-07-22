package com.loopers.infrastructure.ranking;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.ranking.MvProductRankWeekly;

public interface MvProductRankWeeklyJpaRepository extends JpaRepository<MvProductRankWeekly, Long> {

    List<MvProductRankWeekly> findByPeriodKeyOrderByRankAsc(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}
