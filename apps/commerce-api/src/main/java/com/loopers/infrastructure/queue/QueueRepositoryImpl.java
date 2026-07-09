package com.loopers.infrastructure.queue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String WAITING_QUEUE_KEY = "waiting-queue";
    private static final String ENTRY_TOKEN_KEY_PREFIX = "entry-token:";
    private static final RedisScript<Long> ISSUE_ENTRY_TOKEN_SCRIPT = RedisScript.of(
        "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) "
            + "redis.call('ZREM', KEYS[2], ARGV[3]) "
            + "return 1",
        Long.class
    );

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

    @Override
    public List<Long> findFront(int size) {
        if (size <= 0) {
            return List.of();
        }

        Set<String> members = masterRedisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, size - 1);

        if (members == null) {
            return List.of();
        }

        return members.stream()
            .map(Long::valueOf)
            .toList();
    }

    @Override
    public void issueEntryToken(Long userId, String token, Duration ttl) {
        masterRedisTemplate.execute(
            ISSUE_ENTRY_TOKEN_SCRIPT,
            List.of(entryTokenKey(userId), WAITING_QUEUE_KEY),
            token,
            String.valueOf(ttl.getSeconds()),
            String.valueOf(userId)
        );
    }

    private String entryTokenKey(Long userId) {
        return ENTRY_TOKEN_KEY_PREFIX + userId;
    }
}
