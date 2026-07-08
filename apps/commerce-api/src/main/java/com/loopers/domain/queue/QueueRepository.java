package com.loopers.domain.queue;

public interface QueueRepository {

    void add(Long userId);

    long getRank(Long userId);

    long count();
}
