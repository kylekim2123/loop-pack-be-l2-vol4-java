package com.loopers.application.ranking;

import com.loopers.domain.product.projection.ProductSummary;

public record RankingItemInfo(
    Long rank,
    Long productId,
    String name,
    Long brandId,
    String brandName,
    Integer price,
    Boolean isAvailable,
    Integer likeCount
) {

    public static RankingItemInfo of(Long rank, ProductSummary productSummary) {
        return new RankingItemInfo(
            rank,
            productSummary.productId(),
            productSummary.name(),
            productSummary.brandId(),
            productSummary.brandName(),
            productSummary.price(),
            productSummary.isAvailable(),
            productSummary.likeCount()
        );
    }
}
