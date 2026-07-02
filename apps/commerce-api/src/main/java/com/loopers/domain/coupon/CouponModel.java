package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel extends BaseEntity {

    private static final int MIN_MAX_QUANTITY = 1;

    @Embedded
    private Name name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Embedded
    private MinOrderAmount minOrderAmount;

    @Embedded
    private ExpiredAt expiredAt;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Builder
    private CouponModel(String rawName, DiscountType type, Integer rawValue, Integer rawMinOrderAmount, ZonedDateTime rawExpiredAt, ZonedDateTime now, Integer maxQuantity) {
        this.name = Name.from(rawName);
        this.type = type;
        type.validate(rawValue);
        this.discountValue = rawValue;
        this.minOrderAmount = MinOrderAmount.from(rawMinOrderAmount);
        this.expiredAt = ExpiredAt.of(rawExpiredAt, now);
        if (maxQuantity != null && maxQuantity < MIN_MAX_QUANTITY) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("최대 발급 수량은 %d 이상만 허용됩니다.", MIN_MAX_QUANTITY));
        }
        this.maxQuantity = maxQuantity;
        this.issuedCount = 0;
    }

    public void update(String rawName, DiscountType type, Integer rawValue, Integer rawMinOrderAmount, ZonedDateTime rawExpiredAt, ZonedDateTime now) {
        this.name = Name.from(rawName);
        this.type = type;
        type.validate(rawValue);
        this.discountValue = rawValue;
        this.minOrderAmount = MinOrderAmount.from(rawMinOrderAmount);
        this.expiredAt = ExpiredAt.of(rawExpiredAt, now);
    }

    public boolean isExpired(ZonedDateTime now) {
        return expiredAt.isExpired(now);
    }
}
