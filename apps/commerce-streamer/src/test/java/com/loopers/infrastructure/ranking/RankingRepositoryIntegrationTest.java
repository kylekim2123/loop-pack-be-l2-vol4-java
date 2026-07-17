package com.loopers.infrastructure.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.CarryOverResult;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScoreEvent;
import com.loopers.support.ranking.RankingKeyGenerator;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RankingRepositoryIntegrationTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 17);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final Offset<Double> SCORE_TOLERANCE = Offset.offset(0.0001);
    private static final String RANKING_KEY = RankingKeyGenerator.generate(RANKING_DATE);
    private static final double CARRY_OVER_WEIGHT_RATIO = 0.1;
    private static final LocalDate CARRY_OVER_TOMORROW_DATE = RANKING_DATE.plusDays(1);
    private static final String CARRY_OVER_TOMORROW_KEY = RankingKeyGenerator.generate(CARRY_OVER_TOMORROW_DATE);

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String handledKey(String eventId) {
        return "ranking:handled:" + eventId;
    }

    private RankingScoreEvent singleProductEvent(String eventId, long productId, double score) {
        return new RankingScoreEvent(eventId, List.of(new RankingScoreEvent.ProductScore(productId, score)));
    }

    @Nested
    @DisplayName("상품별 점수 합산")
    class ScoreAggregation {

        @DisplayName("같은 상품에 대한 이벤트 점수는 합산되고, 다른 상품은 각자 반영된다.")
        @Test
        void aggregatesScoresPerProduct() {
            // arrange
            List<RankingScoreEvent> events = List.of(
                singleProductEvent("event-1", 1L, 0.1),
                singleProductEvent("event-2", 1L, 0.2),
                singleProductEvent("event-3", 2L, 0.5)
            );

            // act
            rankingRepository.applyScores(RANKING_DATE, events);

            // assert
            assertAll(
                () -> assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "1")).isCloseTo(0.3, SCORE_TOLERANCE),
                () -> assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "2")).isCloseTo(0.5, SCORE_TOLERANCE)
            );
        }

        @DisplayName("15자리 이상의 큰 productId도 과학적 표기 없이 정확한 멤버 문자열로 적재된다.")
        @Test
        void aggregatesScore_whenProductIdHasManyDigits() {
            // arrange
            long largeProductId = 999999999999999L;

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-1", largeProductId, 0.1)));

            // assert
            assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, String.valueOf(largeProductId)))
                .isCloseTo(0.1, SCORE_TOLERANCE);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @DisplayName("동일한 이벤트 배치를 두 번 적재해도 점수는 한 번만 반영된다.")
        @Test
        void appliesScoreOnlyOnce_whenSameBatchAppliedTwice() {
            // arrange
            List<RankingScoreEvent> events = List.of(singleProductEvent("event-1", 1L, 0.3));

            // act
            rankingRepository.applyScores(RANKING_DATE, events);
            rankingRepository.applyScores(RANKING_DATE, events);

            // assert
            assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "1")).isCloseTo(0.3, SCORE_TOLERANCE);
        }
    }

    @Nested
    @DisplayName("음수 점수 반영")
    class NegativeScore {

        @DisplayName("좋아요 취소처럼 음수 점수 이벤트가 반영되면 점수가 감소한다.")
        @Test
        void decreasesScore_whenNegativeScoreEventApplied() {
            // arrange
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-1", 1L, 0.5)));

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-2", 1L, -0.2)));

            // assert
            assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "1")).isCloseTo(0.3, SCORE_TOLERANCE);
        }
    }

    @Nested
    @DisplayName("다품목 이벤트 dedup")
    class MultiItemEventDedup {

        @DisplayName("이벤트 하나에 담긴 여러 상품 점수는 모두 반영되고, 같은 이벤트 재적재 시 모두 무시된다.")
        @Test
        void ignoresAllProductScores_whenSameMultiItemEventReapplied() {
            // arrange
            RankingScoreEvent multiItemEvent = new RankingScoreEvent(
                "event-1",
                List.of(
                    new RankingScoreEvent.ProductScore(1L, 0.4),
                    new RankingScoreEvent.ProductScore(2L, 0.6)
                )
            );

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(multiItemEvent));
            rankingRepository.applyScores(RANKING_DATE, List.of(multiItemEvent));

            // assert
            assertAll(
                () -> assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "1")).isCloseTo(0.4, SCORE_TOLERANCE),
                () -> assertThat(masterRedisTemplate.opsForZSet().score(RANKING_KEY, "2")).isCloseTo(0.6, SCORE_TOLERANCE)
            );
        }
    }

    @Nested
    @DisplayName("랭킹판 만료 시점")
    class RankingKeyExpiry {

        @DisplayName("적재 후 랭킹판 만료는 랭킹 일자 + 2일 자정(Asia/Seoul)으로 고정된다.")
        @Test
        void setsExpiryToFixedMoment_whenRankingKeyFirstCreated() {
            // arrange
            long expectedExpireAtEpochSecond = RANKING_DATE.plusDays(2)
                .atStartOfDay(SEOUL_ZONE)
                .toEpochSecond();

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-1", 1L, 0.1)));

            // assert
            Long remainingTtlSeconds = masterRedisTemplate.getExpire(RANKING_KEY, TimeUnit.SECONDS);
            long actualExpireAtEpochSecond = Instant.now().getEpochSecond() + remainingTtlSeconds;
            assertThat(actualExpireAtEpochSecond).isCloseTo(expectedExpireAtEpochSecond, Offset.offset(5L));
        }
    }

    @Nested
    @DisplayName("TTL 재설정 금지")
    class TtlNotOverwritten {

        @DisplayName("이미 TTL이 설정된 랭킹판에 새 이벤트를 적재해도 TTL이 정책값으로 덮이지 않는다.")
        @Test
        void keepsExistingTtl_whenRankingKeyAlreadyHasExpiry() {
            // arrange
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-1", 1L, 0.1)));
            long arbitraryTtlSeconds = 9999L;
            masterRedisTemplate.expire(RANKING_KEY, Duration.ofSeconds(arbitraryTtlSeconds));

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent("event-2", 1L, 0.2)));

            // assert
            Long remainingTtlSeconds = masterRedisTemplate.getExpire(RANKING_KEY, TimeUnit.SECONDS);
            assertThat(remainingTtlSeconds).isCloseTo(arbitraryTtlSeconds, Offset.offset(5L));
        }
    }

    @Nested
    @DisplayName("handled 키")
    class HandledKey {

        @DisplayName("적재 후 handled 키가 생성되고 TTL이 부여된다.")
        @Test
        void createsHandledKeyWithTtl_whenEventApplied() {
            // arrange
            String eventId = "event-1";

            // act
            rankingRepository.applyScores(RANKING_DATE, List.of(singleProductEvent(eventId, 1L, 0.1)));

            // assert
            assertAll(
                () -> assertThat(masterRedisTemplate.hasKey(handledKey(eventId))).isTrue(),
                () -> assertThat(masterRedisTemplate.getExpire(handledKey(eventId), TimeUnit.SECONDS)).isGreaterThan(0)
            );
        }
    }

    @Nested
    @DisplayName("점수 carry-over")
    class ScoreCarryOver {

        @DisplayName("오늘 판의 각 상품 점수 중 설정된 비율만큼 내일 판에 이월된다.")
        @Test
        void carriesOverWeightedRatioOfEachProductScore() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RANKING_KEY, "1", 100.0);
            masterRedisTemplate.opsForZSet().add(RANKING_KEY, "2", 50.0);

            // act
            rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // assert
            assertAll(
                () -> assertThat(masterRedisTemplate.opsForZSet().score(CARRY_OVER_TOMORROW_KEY, "1")).isCloseTo(10.0, SCORE_TOLERANCE),
                () -> assertThat(masterRedisTemplate.opsForZSet().score(CARRY_OVER_TOMORROW_KEY, "2")).isCloseTo(5.0, SCORE_TOLERANCE)
            );
        }

        @DisplayName("이월된 내일 판의 만료는 내일 일자 + 2일 자정(Asia/Seoul)으로 설정된다.")
        @Test
        void setsExpiryToFixedMoment_whenCarriedOver() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RANKING_KEY, "1", 100.0);
            long expectedExpireAtEpochSecond = CARRY_OVER_TOMORROW_DATE.plusDays(2)
                .atStartOfDay(SEOUL_ZONE)
                .toEpochSecond();

            // act
            rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // assert
            Long remainingTtlSeconds = masterRedisTemplate.getExpire(CARRY_OVER_TOMORROW_KEY, TimeUnit.SECONDS);
            long actualExpireAtEpochSecond = Instant.now().getEpochSecond() + remainingTtlSeconds;
            assertThat(actualExpireAtEpochSecond).isCloseTo(expectedExpireAtEpochSecond, Offset.offset(5L));
        }

        @DisplayName("같은 날짜로 두 번 실행해도 내일 판 점수는 변하지 않고, 두 번째 실행은 skip 결과를 반환한다.")
        @Test
        void skipsSecondRun_whenAlreadyCarriedOver() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RANKING_KEY, "1", 100.0);
            rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // act
            CarryOverResult secondResult = rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // assert
            assertAll(
                () -> assertThat(secondResult).isEqualTo(CarryOverResult.TARGET_ALREADY_EXISTS),
                () -> assertThat(masterRedisTemplate.opsForZSet().score(CARRY_OVER_TOMORROW_KEY, "1")).isCloseTo(10.0, SCORE_TOLERANCE)
            );
        }

        @DisplayName("오늘 판이 존재하지 않으면 아무 키도 생성되지 않고 skip 결과를 반환한다.")
        @Test
        void createsNoKey_whenTodayRankingDoesNotExist() {
            // act
            CarryOverResult result = rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // assert
            assertAll(
                () -> assertThat(result).isEqualTo(CarryOverResult.SOURCE_MISSING),
                () -> assertThat(masterRedisTemplate.hasKey(RANKING_KEY)).isFalse(),
                () -> assertThat(masterRedisTemplate.hasKey(CARRY_OVER_TOMORROW_KEY)).isFalse()
            );
        }

        @DisplayName("내일 판에 이미 점수가 있으면 기존 점수가 그대로 유지되고 skip 결과를 반환한다.")
        @Test
        void keepsExistingScore_whenTomorrowRankingAlreadyExists() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RANKING_KEY, "1", 100.0);
            masterRedisTemplate.opsForZSet().add(CARRY_OVER_TOMORROW_KEY, "1", 999.0);

            // act
            CarryOverResult result = rankingRepository.carryOverScores(RANKING_DATE, CARRY_OVER_WEIGHT_RATIO);

            // assert
            assertAll(
                () -> assertThat(result).isEqualTo(CarryOverResult.TARGET_ALREADY_EXISTS),
                () -> assertThat(masterRedisTemplate.opsForZSet().score(CARRY_OVER_TOMORROW_KEY, "1")).isCloseTo(999.0, SCORE_TOLERANCE)
            );
        }
    }
}
