package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.ranking.RankingPeriod;
import com.loopers.support.ranking.RankingPeriodCalculator;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RankingFacade {

    private static final int MINIMUM_PAGE = 1;
    private static final int MINIMUM_SIZE = 1;
    private static final DateTimeFormatter DAILY_KEY_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd");

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public RankingResult readRankings(RankingPeriodType period, LocalDate rankingDate, int page, int size) {
        int clampedPage = Math.max(page, MINIMUM_PAGE);
        int clampedSize = Math.max(size, MINIMUM_SIZE);

        RankingPeriod window = resolveWindow(period, rankingDate);
        long startIndex = (long) (clampedPage - 1) * clampedSize;

        List<RankedProduct> rankedProducts;
        long totalElements;
        if (period == RankingPeriodType.DAILY) {
            long endIndex = startIndex + clampedSize - 1;
            List<Long> productIds = rankingRepository.findDailyProductIdsByRank(rankingDate, startIndex, endIndex);
            totalElements = rankingRepository.countDailyRanking(rankingDate);
            rankedProducts = withSequentialRanks(productIds, startIndex);
        } else {
            rankedProducts = rankingRepository.findWeeklyOrMonthlyRankedProducts(period, window.periodKey(), clampedPage, clampedSize);
            totalElements = rankingRepository.countWeeklyOrMonthlyRankedProducts(period, window.periodKey());
        }

        List<RankingItemInfo> content = toRankingItemInfos(rankedProducts);
        Page<RankingItemInfo> rankings = new PageImpl<>(content, PageRequest.of(clampedPage - 1, clampedSize), totalElements);

        return new RankingResult(period, window, rankings);
    }

    private RankingPeriod resolveWindow(RankingPeriodType period, LocalDate rankingDate) {
        return switch (period) {
            case DAILY -> new RankingPeriod(DAILY_KEY_FORMATTER.format(rankingDate), rankingDate, rankingDate);
            case WEEKLY -> RankingPeriodCalculator.weekly(rankingDate);
            case MONTHLY -> RankingPeriodCalculator.monthly(rankingDate);
        };
    }

    private List<RankedProduct> withSequentialRanks(List<Long> productIds, long startIndex) {
        List<RankedProduct> rankedProducts = new ArrayList<>();
        for (int index = 0; index < productIds.size(); index++) {
            rankedProducts.add(new RankedProduct(startIndex + index + 1, productIds.get(index)));
        }
        return rankedProducts;
    }

    private List<RankingItemInfo> toRankingItemInfos(List<RankedProduct> rankedProducts) {
        if (rankedProducts.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = rankedProducts.stream().map(RankedProduct::productId).toList();
        Map<Long, ProductSummary> productSummariesByProductId = productRepository.findActiveSummariesByIds(productIds).stream()
            .collect(Collectors.toMap(ProductSummary::productId, Function.identity()));

        List<RankingItemInfo> rankingItemInfos = new ArrayList<>();
        for (RankedProduct rankedProduct : rankedProducts) {
            ProductSummary productSummary = productSummariesByProductId.get(rankedProduct.productId());
            if (productSummary == null) {
                continue;
            }

            rankingItemInfos.add(RankingItemInfo.of(rankedProduct.rank(), productSummary));
        }

        return rankingItemInfos;
    }
}
