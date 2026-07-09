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

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;

    public QueuePositionInfo enter(Long userId) {
        queueRepository.add(userId);

        return readPosition(userId);
    }

    public QueuePositionInfo readPosition(Long userId) {
        long rank = queueRepository.getRank(userId);
        long totalWaiting = queueRepository.count();

        return QueuePositionInfo.of(rank + 1, totalWaiting);
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
