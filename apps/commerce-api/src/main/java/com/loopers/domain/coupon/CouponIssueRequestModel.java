package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupon_issue_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "reason", length = 200)
    private String reason;

    private CouponIssueRequestModel(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }

        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequestModel of(Long userId, Long couponId) {
        return new CouponIssueRequestModel(userId, couponId);
    }

    public void markSuccess() {
        this.status = CouponIssueRequestStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.reason = reason;
    }
}
