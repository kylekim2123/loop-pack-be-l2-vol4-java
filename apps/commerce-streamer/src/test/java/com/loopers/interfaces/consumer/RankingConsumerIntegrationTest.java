package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.support.ranking.RankingKeyGenerator;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RankingConsumerIntegrationTest {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final Offset<Double> SCORE_TOLERANCE = Offset.offset(0.0001);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RankingScoreCalculator rankingScoreCalculator;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    private Map<String, Object> envelope(String eventId, String eventType, Long aggregateId, ZonedDateTime occurredAt, Map<String, Object> data) {
        return Map.of(
            "eventId", eventId,
            "eventType", eventType,
            "aggregateId", String.valueOf(aggregateId),
            "occurredAt", occurredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "data", data
        );
    }

    private void publishLikeCreated(String eventId, Long productId, ZonedDateTime occurredAt) {
        publishLikeCreated(eventId, productId, occurredAt, String.valueOf(productId));
    }

    private void publishLikeCreated(String eventId, Long productId, ZonedDateTime occurredAt, String partitionKey) {
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, partitionKey,
            envelope(eventId, "LIKE_CREATED", productId, occurredAt, Map.of("userId", 1L, "productId", productId)));
    }

    private void publishLikeDeleted(String eventId, Long productId, ZonedDateTime occurredAt) {
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(productId),
            envelope(eventId, "LIKE_DELETED", productId, occurredAt, Map.of("userId", 1L, "productId", productId)));
    }

    private void publishProductViewed(String eventId, Long productId, ZonedDateTime occurredAt) {
        publishProductViewed(eventId, productId, occurredAt, String.valueOf(productId));
    }

    private void publishProductViewed(String eventId, Long productId, ZonedDateTime occurredAt, String partitionKey) {
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, partitionKey,
            envelope(eventId, "PRODUCT_VIEWED", productId, occurredAt, Map.of("productId", productId)));
    }

    private Double scoreOf(LocalDate rankingDate, Long productId) {
        return masterRedisTemplate.opsForZSet().score(RankingKeyGenerator.generate(rankingDate), String.valueOf(productId));
    }

    @DisplayName("종류별 점수 반영")
    @Nested
    class ScoreByEventType {

        @DisplayName("상품 조회 이벤트를 소비하면 오늘 랭킹판에 조회 점수가 반영된다.")
        @Test
        void appliesViewScore_whenProductViewedEventIsConsumed() {
            // arrange
            Long productId = 201L;
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

            // act
            publishProductViewed(UUID.randomUUID().toString(), productId, occurredAt);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(rankingDate, productId)).isCloseTo(rankingScoreCalculator.productViewedScore(), SCORE_TOLERANCE));
        }

        @DisplayName("좋아요 등록 이벤트를 소비하면 오늘 랭킹판에 좋아요 점수가 반영된다.")
        @Test
        void appliesLikeScore_whenLikeCreatedEventIsConsumed() {
            // arrange
            Long productId = 202L;
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

            // act
            publishLikeCreated(UUID.randomUUID().toString(), productId, occurredAt);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(rankingDate, productId)).isCloseTo(rankingScoreCalculator.likeCreatedScore(), SCORE_TOLERANCE));
        }

        @DisplayName("주문 생성 이벤트를 소비하면 오늘 랭킹판에 항목별 주문 점수가 반영된다.")
        @Test
        void appliesOrderItemScore_whenOrderCreatedEventIsConsumed() {
            // arrange
            Long firstProductId = 203L;
            Long secondProductId = 204L;
            long firstPrice = 10_000L;
            long firstQuantity = 2L;
            long secondPrice = 6_000L;
            long secondQuantity = 3L;
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

            // act
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, "1",
                envelope(UUID.randomUUID().toString(), "ORDER_CREATED", 1L, occurredAt,
                    Map.of(
                        "orderId", 1L,
                        "userId", 1L,
                        "finalAmount", 78_000,
                        "items", List.of(
                            Map.of("productId", firstProductId, "quantity", firstQuantity, "price", firstPrice),
                            Map.of("productId", secondProductId, "quantity", secondQuantity, "price", secondPrice)
                        )
                    )));

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
                () -> assertThat(scoreOf(rankingDate, firstProductId))
                    .isCloseTo(rankingScoreCalculator.orderItemScore(firstPrice, firstQuantity), SCORE_TOLERANCE),
                () -> assertThat(scoreOf(rankingDate, secondProductId))
                    .isCloseTo(rankingScoreCalculator.orderItemScore(secondPrice, secondQuantity), SCORE_TOLERANCE)
            ));
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class LikeCancellation {

        @DisplayName("좋아요 등록 후 취소 이벤트가 오면 점수가 0으로 되돌아간다.")
        @Test
        void revertsScoreToZero_whenLikeDeletedEventFollowsLikeCreated() {
            // arrange
            Long productId = 205L;
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();
            publishLikeCreated(UUID.randomUUID().toString(), productId, occurredAt);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(rankingDate, productId)).isCloseTo(rankingScoreCalculator.likeCreatedScore(), SCORE_TOLERANCE));

            // act
            publishLikeDeleted(UUID.randomUUID().toString(), productId, occurredAt);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(rankingDate, productId)).isCloseTo(0.0, SCORE_TOLERANCE));
        }
    }

    @DisplayName("동일 이벤트 재발행")
    @Nested
    class DuplicateEvent {

        @DisplayName("같은 이벤트를 두 번 발행해도 점수는 한 번만 반영된다.")
        @Test
        void appliesScoreOnlyOnce_whenSameEventIsPublishedTwice() {
            // arrange
            Long productId = 206L;
            Long markerProductId = 207L;
            String eventId = UUID.randomUUID().toString();
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();
            String partitionKey = String.valueOf(productId);

            // act
            publishLikeCreated(eventId, productId, occurredAt, partitionKey);
            publishLikeCreated(eventId, productId, occurredAt, partitionKey);
            publishProductViewed(UUID.randomUUID().toString(), markerProductId, occurredAt, partitionKey);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(rankingDate, markerProductId)).isCloseTo(rankingScoreCalculator.productViewedScore(), SCORE_TOLERANCE));
            assertThat(scoreOf(rankingDate, productId)).isCloseTo(rankingScoreCalculator.likeCreatedScore(), SCORE_TOLERANCE);
        }
    }

    @DisplayName("가중치 순서 반영")
    @Nested
    class WeightedOrdering {

        @DisplayName("주문 1건 상품이 좋아요 3건 상품보다 랭킹판 순위가 높다.")
        @Test
        void ranksOrderedProductAboveTripleLikedProduct_whenOrderAndThreeLikesAreConsumed() {
            // arrange
            Long orderedProductId = 209L;
            Long likedProductId = 210L;
            long unitPrice = 50_000L;
            long quantity = 1L;
            ZonedDateTime occurredAt = ZonedDateTime.now();
            LocalDate rankingDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();
            String rankingKey = RankingKeyGenerator.generate(rankingDate);

            // act
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, "1",
                envelope(UUID.randomUUID().toString(), "ORDER_CREATED", 1L, occurredAt,
                    Map.of(
                        "orderId", 1L,
                        "userId", 1L,
                        "finalAmount", unitPrice * quantity,
                        "items", List.of(
                            Map.of("productId", orderedProductId, "quantity", quantity, "price", unitPrice)
                        )
                    )));
            publishLikeCreated(UUID.randomUUID().toString(), likedProductId, occurredAt);
            publishLikeCreated(UUID.randomUUID().toString(), likedProductId, occurredAt);
            publishLikeCreated(UUID.randomUUID().toString(), likedProductId, occurredAt);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
                () -> assertThat(scoreOf(rankingDate, orderedProductId))
                    .isCloseTo(rankingScoreCalculator.orderItemScore(unitPrice, quantity), SCORE_TOLERANCE),
                () -> assertThat(scoreOf(rankingDate, likedProductId))
                    .isCloseTo(rankingScoreCalculator.likeCreatedScore() * 3, SCORE_TOLERANCE),
                () -> assertThat(masterRedisTemplate.opsForZSet().reverseRank(rankingKey, String.valueOf(orderedProductId)))
                    .isLessThan(masterRedisTemplate.opsForZSet().reverseRank(rankingKey, String.valueOf(likedProductId)))
            ));
        }
    }

    @DisplayName("날짜별 키 분리")
    @Nested
    class RankingDateSeparation {

        @DisplayName("어제 발생한 이벤트는 어제 랭킹판에만 반영되고 오늘 랭킹판에는 반영되지 않는다.")
        @Test
        void appliesScoreOnlyToYesterdayKey_whenEventOccurredYesterday() {
            // arrange
            Long productId = 208L;
            ZonedDateTime yesterday = ZonedDateTime.now(SEOUL_ZONE).minusDays(1);
            ZonedDateTime today = ZonedDateTime.now(SEOUL_ZONE);
            LocalDate yesterdayRankingDate = yesterday.toLocalDate();
            LocalDate todayRankingDate = today.toLocalDate();

            // act
            publishLikeCreated(UUID.randomUUID().toString(), productId, yesterday);

            // assert
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(scoreOf(yesterdayRankingDate, productId)).isCloseTo(rankingScoreCalculator.likeCreatedScore(), SCORE_TOLERANCE));
            assertThat(scoreOf(todayRankingDate, productId)).isNull();
        }
    }
}
