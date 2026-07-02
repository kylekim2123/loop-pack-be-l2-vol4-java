package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderCreatedEvent(Long orderId, Long userId, int finalAmount) {

    public OrderCreatedEvent {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    public static OrderCreatedEvent of(Long orderId, Long userId, int finalAmount) {
        return new OrderCreatedEvent(orderId, userId, finalAmount);
    }
}
