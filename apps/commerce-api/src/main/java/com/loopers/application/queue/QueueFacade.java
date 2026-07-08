package com.loopers.application.queue;

import org.springframework.stereotype.Service;

import com.loopers.domain.queue.QueueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueFacade {

    private final QueueRepository queueRepository;

    public QueuePositionInfo enter(Long userId) {
        queueRepository.add(userId);

        return readPosition(userId);
    }

    public QueuePositionInfo readPosition(Long userId) {
        long rank = queueRepository.getRank(userId);
        long totalWaiting = queueRepository.count();

        return QueuePositionInfo.of(rank + 1, totalWaiting);
    }
}
