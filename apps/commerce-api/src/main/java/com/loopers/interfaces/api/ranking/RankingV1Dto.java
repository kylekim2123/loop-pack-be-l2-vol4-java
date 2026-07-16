package com.loopers.interfaces.api.ranking;

import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.ranking.RankingItemInfo;

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
        List<ItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<RankingItemInfo> rankings) {
            List<ItemResponse> content = rankings.getContent()
                .stream()
                .map(ItemResponse::from)
                .toList();

            return new PageResponse(
                content,
                rankings.getNumber() + 1,
                rankings.getSize(),
                rankings.getTotalElements(),
                rankings.getTotalPages()
            );
        }
    }
}
