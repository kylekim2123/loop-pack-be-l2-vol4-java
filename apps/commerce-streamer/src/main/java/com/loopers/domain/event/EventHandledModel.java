package com.loopers.domain.event;

import com.loopers.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_handled_event_id", columnNames = "event_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    private EventHandledModel(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("이벤트 ID는 필수입니다.");
        }

        this.eventId = eventId;
    }

    public static EventHandledModel from(String eventId) {
        return new EventHandledModel(eventId);
    }
}
