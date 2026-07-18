package com.loopers.application.event;

import com.fasterxml.jackson.databind.JsonNode;

public final class CouponIssueRequestedEventParser {

    private CouponIssueRequestedEventParser() {
    }

    public static CouponIssueRequestedEvent parse(JsonNode envelope) {
        String eventId = envelope.path("eventId").asText(null);
        JsonNode data = envelope.path("data");

        return new CouponIssueRequestedEvent(
            eventId,
            data.path("requestId").asLong(),
            data.path("userId").asLong(),
            data.path("couponId").asLong()
        );
    }
}
