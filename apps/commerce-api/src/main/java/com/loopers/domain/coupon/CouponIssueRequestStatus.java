package com.loopers.domain.coupon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponIssueRequestStatus {

    PENDING("처리 대기"),
    SUCCESS("발급 완료"),
    FAILED("발급 실패");

    private final String description;
}
