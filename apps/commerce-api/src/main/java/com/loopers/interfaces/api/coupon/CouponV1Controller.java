package com.loopers.interfaces.api.coupon;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.IssueRequestResponse> createCouponIssueRequest(
        @PathVariable Long couponId,
        @LoginUser AuthenticatedUser loginUser
    ) {
        CouponIssueRequestInfo issueRequestInfo = couponFacade.createCouponIssueRequest(loginUser.userId(), couponId, dateTimeUtil.now());

        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(issueRequestInfo));
    }
}
