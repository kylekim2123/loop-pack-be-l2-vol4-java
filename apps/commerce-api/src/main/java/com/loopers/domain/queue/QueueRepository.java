package com.loopers.domain.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface QueueRepository {

    void add(Long userId);

    long getRank(Long userId);

    long count();

    List<Long> findFront(int size);

    void issueEntryToken(Long userId, String token, Duration ttl);

    Optional<String> findEntryToken(Long userId);

    Optional<Duration> consumeEntryToken(Long userId, String token);

    void restoreEntryToken(Long userId, String token, Duration ttl);
}
