package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record LikeCreatedEvent(Long productId) {

    public LikeCreatedEvent {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    public static LikeCreatedEvent from(Long productId) {
        return new LikeCreatedEvent(productId);
    }
}
