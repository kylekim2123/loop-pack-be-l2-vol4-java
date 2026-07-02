package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record OutboxEnvelope(String eventId, String eventType, String aggregateId, ZonedDateTime occurredAt, Object data) {

    public OutboxEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 ID는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 타입은 필수입니다.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "집계 대상 ID는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발생 시각은 필수입니다.");
        }
        if (data == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 데이터는 필수입니다.");
        }
    }
}
