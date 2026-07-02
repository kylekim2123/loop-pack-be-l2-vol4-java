package com.loopers.domain.order;

import java.util.List;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OrderCreatedEvent(Long orderId, Long userId, int finalAmount, List<Item> items) {

    public record Item(Long productId, int quantity) {
    }

    public OrderCreatedEvent {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 필수입니다.");
        }
        items = List.copyOf(items);
    }

    public static OrderCreatedEvent of(Long orderId, Long userId, int finalAmount, List<Item> items) {
        return new OrderCreatedEvent(orderId, userId, finalAmount, items);
    }
}
