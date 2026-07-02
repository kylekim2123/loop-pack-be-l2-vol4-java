package com.loopers.domain.coupon;

public interface CouponIssueRequestRepository {

    CouponIssueRequestModel save(CouponIssueRequestModel issueRequest);

    CouponIssueRequestModel getActiveByIdAndUserId(Long requestId, Long userId);
}
