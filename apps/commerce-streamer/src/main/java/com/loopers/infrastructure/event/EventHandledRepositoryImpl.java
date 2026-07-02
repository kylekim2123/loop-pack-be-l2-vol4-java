package com.loopers.infrastructure.event;

import org.springframework.stereotype.Component;

import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public EventHandledModel save(EventHandledModel eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }
}
