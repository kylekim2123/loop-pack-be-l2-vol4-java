package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record PaymentFailedEvent(Long paymentId, Long orderId) {

    public PaymentFailedEvent {
        if (paymentId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 ID는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    public static PaymentFailedEvent of(Long paymentId, Long orderId) {
        return new PaymentFailedEvent(paymentId, orderId);
    }
}
