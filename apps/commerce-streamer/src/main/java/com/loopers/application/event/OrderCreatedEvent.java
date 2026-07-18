package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderCreatedEvent(String eventId, ZonedDateTime occurredAt, List<Item> items) implements ProductActivityEvent {

    public OrderCreatedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("발생 시각은 필수입니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목은 필수입니다.");
        }
    }

    public record Item(Long productId, long quantity, long price) {

        public Item {
            if (productId == null) {
                throw new IllegalArgumentException("상품 ID는 필수입니다.");
            }
        }

        public long salesAmount() {
            return price * quantity;
        }
    }
}
