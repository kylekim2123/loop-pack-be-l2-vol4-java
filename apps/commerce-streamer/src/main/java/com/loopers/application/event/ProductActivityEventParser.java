package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ProductActivityEventParser {

    private static final String LIKE_CREATED = "LIKE_CREATED";
    private static final String LIKE_DELETED = "LIKE_DELETED";
    private static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String ORDER_CREATED = "ORDER_CREATED";

    private ProductActivityEventParser() {
    }

    public static Optional<ProductActivityEvent> parse(JsonNode envelope) {
        String eventId = envelope.path("eventId").asText(null);
        String eventType = envelope.path("eventType").asText(null);
        ZonedDateTime occurredAt = ZonedDateTime.parse(envelope.path("occurredAt").asText());
        JsonNode data = envelope.path("data");

        return switch (eventType) {
            case LIKE_CREATED -> Optional.of(new LikeCreatedEvent(eventId, occurredAt, data.path("productId").asLong()));
            case LIKE_DELETED -> Optional.of(new LikeDeletedEvent(eventId, occurredAt, data.path("productId").asLong()));
            case PRODUCT_VIEWED -> Optional.of(new ProductViewedEvent(eventId, occurredAt, data.path("productId").asLong()));
            case ORDER_CREATED -> Optional.of(new OrderCreatedEvent(eventId, occurredAt, parseItems(data.path("items"))));
            default -> {
                log.warn("집계 대상이 아닌 이벤트 타입 - 건너뜀 (eventId={}, eventType={})", eventId, eventType);
                yield Optional.empty();
            }
        };
    }

    private static List<OrderCreatedEvent.Item> parseItems(JsonNode items) {
        List<OrderCreatedEvent.Item> parsedItems = new ArrayList<>();
        for (JsonNode item : items) {
            parsedItems.add(new OrderCreatedEvent.Item(
                item.path("productId").asLong(),
                item.path("quantity").asLong(),
                item.path("price").asLong()
            ));
        }
        return parsedItems;
    }
}
