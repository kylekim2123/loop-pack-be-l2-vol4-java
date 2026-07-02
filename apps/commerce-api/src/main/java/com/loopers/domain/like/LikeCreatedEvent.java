package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record LikeCreatedEvent(Long userId, Long productId) {

    public LikeCreatedEvent {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    public static LikeCreatedEvent of(Long userId, Long productId) {
        return new LikeCreatedEvent(userId, productId);
    }
}
