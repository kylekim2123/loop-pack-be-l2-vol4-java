package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueRequestInfo;

public class CouponV1Dto {

    public record IssueRequestResponse(Long requestId, String status, String reason) {

        public static IssueRequestResponse from(CouponIssueRequestInfo issueRequestInfo) {
            return new IssueRequestResponse(
                issueRequestInfo.requestId(),
                issueRequestInfo.status().name(),
                issueRequestInfo.reason()
            );
        }
    }
}
