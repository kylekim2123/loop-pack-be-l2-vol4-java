package com.loopers.application.product;

public record ProductDetailWithRankInfo(
    ProductDetailInfo detail,
    Long rank
) {

    public static ProductDetailWithRankInfo of(ProductDetailInfo detail, Long rank) {
        return new ProductDetailWithRankInfo(detail, rank);
    }
}
