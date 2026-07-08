package com.loopers.infrastructure.queue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String WAITING_QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> masterRedisTemplate;

    public QueueRepositoryImpl(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void add(Long userId) {
        masterRedisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, String.valueOf(userId), System.currentTimeMillis());
    }

    @Override
    public long getRank(Long userId) {
        Long rank = masterRedisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, String.valueOf(userId));

        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않았습니다.");
        }

        return rank;
    }

    @Override
    public long count() {
        Long size = masterRedisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);

        return size == null ? 0 : size;
    }
}
