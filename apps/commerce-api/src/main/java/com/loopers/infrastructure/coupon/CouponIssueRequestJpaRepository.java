package com.loopers.infrastructure.coupon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.CouponIssueRequestModel;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, Long> {

    Optional<CouponIssueRequestModel> findByIdAndUserIdAndDeletedAtIsNull(Long requestId, Long userId);
}
