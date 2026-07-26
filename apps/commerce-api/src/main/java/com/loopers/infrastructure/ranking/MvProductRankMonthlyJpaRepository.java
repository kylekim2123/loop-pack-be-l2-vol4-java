package com.loopers.infrastructure.ranking;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.ranking.MvProductRankMonthly;

public interface MvProductRankMonthlyJpaRepository extends JpaRepository<MvProductRankMonthly, Long> {

    List<MvProductRankMonthly> findByPeriodKeyOrderByRankAsc(String periodKey, Pageable pageable);

    long countByPeriodKey(String periodKey);
}
