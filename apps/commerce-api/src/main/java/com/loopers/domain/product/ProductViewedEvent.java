package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record ProductViewedEvent(Long productId) {

    public ProductViewedEvent {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    public static ProductViewedEvent from(Long productId) {
        return new ProductViewedEvent(productId);
    }
}
