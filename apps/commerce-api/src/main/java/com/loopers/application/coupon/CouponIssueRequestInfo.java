package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestStatus;

public record CouponIssueRequestInfo(Long requestId, CouponIssueRequestStatus status, String reason) {

    public static CouponIssueRequestInfo from(CouponIssueRequestModel issueRequest) {
        return new CouponIssueRequestInfo(issueRequest.getId(), issueRequest.getStatus(), issueRequest.getReason());
    }
}
