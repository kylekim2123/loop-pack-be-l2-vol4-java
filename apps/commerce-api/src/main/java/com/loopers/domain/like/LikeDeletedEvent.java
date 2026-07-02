package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record LikeDeletedEvent(Long userId, Long productId) {

    public LikeDeletedEvent {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
    }

    public static LikeDeletedEvent of(Long userId, Long productId) {
        return new LikeDeletedEvent(userId, productId);
    }
}
