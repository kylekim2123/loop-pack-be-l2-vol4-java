package com.loopers.application.event;

import java.time.ZonedDateTime;

public sealed interface ProductActivityEvent
    permits LikeCreatedEvent, LikeDeletedEvent, ProductViewedEvent, OrderCreatedEvent {

    String eventId();

    ZonedDateTime occurredAt();
}
