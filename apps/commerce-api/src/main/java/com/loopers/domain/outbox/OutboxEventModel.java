package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "outbox_events",
    indexes = @Index(name = "idx_outbox_events_published_at", columnList = "published_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventModel extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Builder
    private OutboxEventModel(String eventId, String topic, String partitionKey, String eventType, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 ID는 필수입니다.");
        }
        if (topic == null || topic.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "토픽은 필수입니다.");
        }
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "파티션 키는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 타입은 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이로드는 필수입니다.");
        }

        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markPublished(ZonedDateTime now) {
        this.publishedAt = now;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
