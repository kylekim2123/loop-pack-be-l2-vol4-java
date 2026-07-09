package com.loopers.application.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.loopers.domain.queue.QueueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueFacade {

    private static final long MILLIS_PER_SECOND = 1_000L;

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;

    public QueuePositionInfo enter(Long userId) {
        queueRepository.add(userId);

        return readWaitingPosition(userId);
    }

    public QueuePositionInfo readPosition(Long userId) {
        return queueRepository.findEntryToken(userId)
            .map(QueuePositionInfo::issued)
            .orElseGet(() -> readWaitingPosition(userId));
    }

    private QueuePositionInfo readWaitingPosition(Long userId) {
        long position = queueRepository.getRank(userId) + 1;
        long totalWaiting = queueRepository.count();

        return QueuePositionInfo.waiting(position, totalWaiting, estimateWaitSeconds(position));
    }

    private long estimateWaitSeconds(long position) {
        long issuesPerSecond = queueProperties.batchSize() * MILLIS_PER_SECOND / queueProperties.issueInterval().toMillis();

        return Math.ceilDiv(position, issuesPerSecond);
    }

    public List<Long> findIssuableUserIds() {
        return queueRepository.findFront(queueProperties.batchSize());
    }

    public void issueEntryToken(Long userId) {
        String token = UUID.randomUUID().toString();

        queueRepository.issueEntryToken(userId, token, queueProperties.tokenTtl());
    }

    public Optional<Duration> consumeEntryToken(Long userId, String token) {
        return queueRepository.consumeEntryToken(userId, token);
    }

    public void restoreEntryToken(Long userId, String token, Duration ttl) {
        queueRepository.restoreEntryToken(userId, token, ttl);
    }
}
