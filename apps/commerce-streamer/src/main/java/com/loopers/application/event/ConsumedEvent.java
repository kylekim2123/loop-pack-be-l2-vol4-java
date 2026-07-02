package com.loopers.application.event;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.databind.JsonNode;

public record ConsumedEvent(String eventId, String eventType, ZonedDateTime occurredAt, JsonNode data) {

    public ConsumedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("이벤트 타입은 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("발생 시각은 필수입니다.");
        }
        if (data == null) {
            throw new IllegalArgumentException("이벤트 데이터는 필수입니다.");
        }
    }

    public static ConsumedEvent from(JsonNode envelope) {
        return new ConsumedEvent(
            envelope.path("eventId").asText(null),
            envelope.path("eventType").asText(null),
            ZonedDateTime.parse(envelope.path("occurredAt").asText()),
            envelope.path("data")
        );
    }
}
