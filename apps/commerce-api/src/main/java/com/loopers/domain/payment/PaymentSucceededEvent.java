package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record PaymentSucceededEvent(Long paymentId, Long orderId, int amount) {

    public PaymentSucceededEvent {
        if (paymentId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 ID는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    public static PaymentSucceededEvent of(Long paymentId, Long orderId, int amount) {
        return new PaymentSucceededEvent(paymentId, orderId, amount);
    }
}
