package com.loopers.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class QueueRepositoryIntegrationTest {

    private static final String WAITING_QUEUE_KEY = "waiting-queue";

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seedWaiting(long userId, double score) {
        masterRedisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, String.valueOf(userId), score);
    }

    @DisplayName("findFront는 대기열 앞에서 score가 작은 순서대로 지정한 인원만 반환한다.")
    @Test
    void returnsFrontUsersInScoreOrder() {
        // arrange
        seedWaiting(100L, 3);
        seedWaiting(200L, 1);
        seedWaiting(300L, 2);

        // act
        List<Long> front = queueRepository.findFront(2);

        // assert
        assertThat(front).containsExactly(200L, 300L);
    }

    @DisplayName("issueEntryToken은 입장권을 TTL과 함께 저장하고, 같은 원자 실행으로 대기열에서 제거한다.")
    @Test
    void issuesTokenWithTtlAndRemovesFromQueueAtomically() {
        // arrange
        seedWaiting(100L, 1);

        // act
        queueRepository.issueEntryToken(100L, "tok-abc", Duration.ofMinutes(5));

        // assert
        String storedToken = masterRedisTemplate.opsForValue().get("entry-token:100");
        Long ttlSeconds = masterRedisTemplate.getExpire("entry-token:100", TimeUnit.SECONDS);
        assertAll(
            () -> assertThat(storedToken).isEqualTo("tok-abc"),
            () -> assertThat(ttlSeconds).isBetween(1L, 300L),
            () -> assertThat(queueRepository.count()).isZero()
        );
    }
}
