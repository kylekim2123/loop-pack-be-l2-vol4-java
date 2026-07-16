package com.loopers.application.ranking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.projection.ProductSummary;
import com.loopers.domain.ranking.RankingRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RankingFacade {

    private static final int MINIMUM_PAGE = 1;
    private static final int MINIMUM_SIZE = 1;

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<RankingItemInfo> readRankings(LocalDate rankingDate, int page, int size) {
        int clampedPage = Math.max(page, MINIMUM_PAGE);
        int clampedSize = Math.max(size, MINIMUM_SIZE);

        long startIndex = (long) (clampedPage - 1) * clampedSize;
        long endIndex = startIndex + clampedSize - 1;

        List<Long> rankedProductIds = rankingRepository.findProductIdsByRank(rankingDate, startIndex, endIndex);
        long totalElements = rankingRepository.countByRankingDate(rankingDate);

        List<RankingItemInfo> content = toRankingItemInfos(rankedProductIds, startIndex);

        return new PageImpl<>(content, PageRequest.of(clampedPage - 1, clampedSize), totalElements);
    }

    private List<RankingItemInfo> toRankingItemInfos(List<Long> rankedProductIds, long startIndex) {
        if (rankedProductIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductSummary> productSummariesByProductId = productRepository.findActiveSummariesByIds(rankedProductIds).stream()
            .collect(Collectors.toMap(ProductSummary::productId, Function.identity()));

        List<RankingItemInfo> rankingItemInfos = new ArrayList<>();
        for (int index = 0; index < rankedProductIds.size(); index++) {
            ProductSummary productSummary = productSummariesByProductId.get(rankedProductIds.get(index));
            if (productSummary == null) {
                continue;
            }

            long rank = startIndex + index + 1;
            rankingItemInfos.add(RankingItemInfo.of(rank, productSummary));
        }

        return rankingItemInfos;
    }
}
