package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.ranking.RankingKeyGenerator;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<Long> findProductIdsByRank(LocalDate rankingDate, long startIndex, long endIndex) {
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
    public long countByRankingDate(LocalDate rankingDate) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        Long totalCount = redisTemplate.opsForZSet().zCard(rankingKey);

        return totalCount == null ? 0 : totalCount;
    }

    @Override
    public Optional<Long> findRank(LocalDate rankingDate, Long productId) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(rankingKey, String.valueOf(productId));

        return Optional.ofNullable(zeroBasedRank);
    }
}
