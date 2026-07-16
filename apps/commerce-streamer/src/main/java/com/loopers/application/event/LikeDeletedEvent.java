package com.loopers.application.event;

import java.time.ZonedDateTime;

public record LikeDeletedEvent(String eventId, ZonedDateTime occurredAt, Long productId) implements ProductActivityEvent {

    public LikeDeletedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("발생 시각은 필수입니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
    }
}
