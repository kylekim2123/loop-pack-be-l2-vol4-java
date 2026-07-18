package com.loopers.application.event;

public record CouponIssueRequestedEvent(String eventId, Long requestId, Long userId, Long couponId) {

    public CouponIssueRequestedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("발급 요청 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (couponId == null) {
            throw new IllegalArgumentException("쿠폰 ID는 필수입니다.");
        }
    }
}
