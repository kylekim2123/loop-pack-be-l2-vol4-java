package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
class EntryTokenSchedulerIntegrationTest {

    private static final String WAITING_QUEUE_KEY = "waiting-queue";

    @Autowired
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueProperties queueProperties;

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

    private boolean hasToken(long userId) {
        return Boolean.TRUE.equals(masterRedisTemplate.hasKey("entry-token:" + userId));
    }

    @DisplayName("대기 인원이 배치 크기보다 많으면, 앞에서 배치 크기만큼만 입장권을 발급하고 나머지는 대기열에 남긴다.")
    @Test
    void issuesOnlyBatchSize_whenWaitingExceedsBatch() {
        // arrange
        int batchSize = queueProperties.batchSize();
        int remaining = 6;
        for (int userId = 1; userId <= batchSize + remaining; userId++) {
            seedWaiting(userId, userId);
        }

        // act
        entryTokenScheduler.issueEntryTokens();

        // assert
        assertAll(
            () -> assertThat(queueRepository.count()).isEqualTo(remaining),
            () -> assertThat(hasToken(1)).isTrue(),
            () -> assertThat(hasToken(batchSize)).isTrue(),
            () -> assertThat(hasToken(batchSize + 1)).isFalse()
        );
    }

    @DisplayName("대기 인원이 배치 크기보다 적으면, 대기 중인 전원에게 입장권을 발급하고 대기열을 비운다.")
    @Test
    void issuesAll_whenWaitingBelowBatch() {
        // arrange
        seedWaiting(1, 1);
        seedWaiting(2, 2);
        seedWaiting(3, 3);

        // act
        entryTokenScheduler.issueEntryTokens();

        // assert
        assertAll(
            () -> assertThat(queueRepository.count()).isZero(),
            () -> assertThat(hasToken(1)).isTrue(),
            () -> assertThat(hasToken(2)).isTrue(),
            () -> assertThat(hasToken(3)).isTrue()
        );
    }
}
