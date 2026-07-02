package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 도메인 회원 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급 요청 접수",
        description = "회원의 쿠폰 발급 요청을 접수하고 requestId를 즉시 반환한다. 실제 발급은 비동기로 처리되며, requestId로 결과를 조회할 수 있다."
    )
    ApiResponse<CouponV1Dto.IssueRequestResponse> createCouponIssueRequest(
        Long couponId,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );

    @Operation(
        summary = "쿠폰 발급 결과 조회",
        description = "requestId로 본인 발급 요청의 진행 상태(PENDING/SUCCESS/FAILED)와 실패 사유를 조회한다."
    )
    ApiResponse<CouponV1Dto.IssueRequestResponse> readCouponIssueRequest(
        Long requestId,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
