package com.loopers.domain.event;

public interface EventHandledRepository {

    EventHandledModel save(EventHandledModel eventHandled);

    boolean existsByEventId(String eventId);
}
