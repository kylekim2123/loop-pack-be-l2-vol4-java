package com.loopers.interfaces.api.ranking;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingResult;

public class RankingV1Dto {

    public record BrandResponse(Long brandId, String name) {
    }

    public record ItemResponse(
        Long rank,
        Long productId,
        String name,
        BrandResponse brand,
        Integer price,
        Boolean isAvailable,
        Integer likeCount
    ) {

        public static ItemResponse from(RankingItemInfo rankingItemInfo) {
            return new ItemResponse(
                rankingItemInfo.rank(),
                rankingItemInfo.productId(),
                rankingItemInfo.name(),
                new BrandResponse(rankingItemInfo.brandId(), rankingItemInfo.brandName()),
                rankingItemInfo.price(),
                rankingItemInfo.isAvailable(),
                rankingItemInfo.likeCount()
            );
        }
    }

    public record PageResponse(
        String period,
        String periodKey,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(RankingResult result) {
            Page<RankingItemInfo> rankings = result.rankings();
            List<ItemResponse> content = rankings.getContent()
                .stream()
                .map(ItemResponse::from)
                .toList();

            return new PageResponse(
                result.period().name(),
                result.window().periodKey(),
                result.window().startDate(),
                result.window().endDate(),
                content,
                rankings.getNumber() + 1,
                rankings.getSize(),
                rankings.getTotalElements(),
                rankings.getTotalPages()
            );
        }
    }
}
