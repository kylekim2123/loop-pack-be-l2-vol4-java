package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;

public interface QueueRepository {

    void add(Long userId);

    long getRank(Long userId);

    long count();

    List<Long> findFront(int size);

    void issueEntryToken(Long userId, String token, Duration ttl);
}
