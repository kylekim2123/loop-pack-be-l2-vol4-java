package com.loopers.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.event.EventHandledModel;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, Long> {

    boolean existsByEventId(String eventId);
}
