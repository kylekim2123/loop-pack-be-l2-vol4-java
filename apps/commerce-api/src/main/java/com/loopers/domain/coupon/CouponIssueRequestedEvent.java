package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CouponIssueRequestedEvent(Long requestId, Long userId, Long couponId) {

    public CouponIssueRequestedEvent {
        if (requestId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 요청 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
    }

    public static CouponIssueRequestedEvent of(Long requestId, Long userId, Long couponId) {
        return new CouponIssueRequestedEvent(requestId, userId, couponId);
    }
}
