package com.loopers.infrastructure.coupon;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel issueRequest) {
        return couponIssueRequestJpaRepository.save(issueRequest);
    }

    @Override
    public CouponIssueRequestModel getActiveByIdAndUserId(Long requestId, Long userId) {
        return couponIssueRequestJpaRepository.findByIdAndUserIdAndDeletedAtIsNull(requestId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청이 존재하지 않습니다."));
    }
}
