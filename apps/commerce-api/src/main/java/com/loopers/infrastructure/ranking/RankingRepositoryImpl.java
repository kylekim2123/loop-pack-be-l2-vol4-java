package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.ranking.RankingKeyGenerator;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<Long> findDailyProductIdsByRank(LocalDate rankingDate, long startIndex, long endIndex) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        Set<String> productIds = redisTemplate.opsForZSet().reverseRange(rankingKey, startIndex, endIndex);

        if (productIds == null) {
            return List.of();
        }

        return productIds.stream()
            .map(Long::valueOf)
            .toList();
    }

    @Override
    public long countDailyRanking(LocalDate rankingDate) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        Long totalCount = redisTemplate.opsForZSet().zCard(rankingKey);

        return totalCount == null ? 0 : totalCount;
    }

    @Override
    public Optional<Long> findDailyRank(LocalDate rankingDate, Long productId) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(rankingKey, String.valueOf(productId));

        return Optional.ofNullable(zeroBasedRank);
    }

    @Override
    public List<RankedProduct> findWeeklyOrMonthlyRankedProducts(RankingPeriodType periodType, String periodKey, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);

        return switch (periodType) {
            case WEEKLY -> weeklyJpaRepository.findByPeriodKeyOrderByRankAsc(periodKey, pageable).stream()
                .map(row -> new RankedProduct(row.getRank(), row.getProductId()))
                .toList();
            case MONTHLY -> monthlyJpaRepository.findByPeriodKeyOrderByRankAsc(periodKey, pageable).stream()
                .map(row -> new RankedProduct(row.getRank(), row.getProductId()))
                .toList();
            case DAILY -> throw new CoreException(ErrorType.INTERNAL_ERROR, "주간·월간 랭킹 조회에는 일간 기간을 사용할 수 없습니다.");
        };
    }

    @Override
    public long countWeeklyOrMonthlyRankedProducts(RankingPeriodType periodType, String periodKey) {
        return switch (periodType) {
            case WEEKLY -> weeklyJpaRepository.countByPeriodKey(periodKey);
            case MONTHLY -> monthlyJpaRepository.countByPeriodKey(periodKey);
            case DAILY -> throw new CoreException(ErrorType.INTERNAL_ERROR, "주간·월간 랭킹 조회에는 일간 기간을 사용할 수 없습니다.");
        };
    }
}
