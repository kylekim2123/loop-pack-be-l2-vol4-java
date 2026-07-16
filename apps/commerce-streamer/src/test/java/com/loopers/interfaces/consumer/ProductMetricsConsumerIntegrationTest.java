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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import com.loopers.domain.metrics.ProductMetricsDailyModel;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductMetricsConsumerIntegrationTest {

    private static final String CATALOG_EVENTS_TOPIC = "catalog-events";
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
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
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(productId),
            envelope(eventId, "LIKE_CREATED", productId, occurredAt, Map.of("userId", 1L, "productId", productId)));
    }

    private ProductMetricsModel metricsOf(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId)
            .orElseGet(() -> {
                throw new AssertionError(String.format("product_metrics 행이 아직 없습니다 (productId=%d)", productId));
            });
    }

    private ProductMetricsDailyModel dailyMetricsOf(Long productId, LocalDate metricDate) {
        return productMetricsDailyJpaRepository.findByProductIdAndMetricDate(productId, metricDate)
            .orElseGet(() -> {
                throw new AssertionError(String.format(
                    "product_metrics_daily 행이 아직 없습니다 (productId=%d, metricDate=%s)", productId, metricDate));
            });
    }

    @DisplayName("좋아요 등록 이벤트를 소비하면 product_metrics와 product_metrics_daily의 좋아요 수가 함께 증가한다.")
    @Test
    void increasesLikeCount_inBothAggregateAndDailyMetrics_whenLikeCreatedEventIsConsumed() {
        // arrange
        Long productId = 101L;
        ZonedDateTime occurredAt = ZonedDateTime.now();
        LocalDate metricDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

        // act
        publishLikeCreated(UUID.randomUUID().toString(), productId, occurredAt);

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L),
            () -> assertThat(dailyMetricsOf(productId, metricDate).getLikeCount()).isEqualTo(1L)
        ));
    }

    @DisplayName("같은 이벤트를 두 번 소비해도 집계는 한 번만 반영된다.")
    @Test
    void aggregatesOnlyOnce_whenSameEventIsConsumedTwice() {
        // arrange
        Long productId = 102L;
        String eventId = UUID.randomUUID().toString();
        ZonedDateTime occurredAt = ZonedDateTime.now();

        // act
        publishLikeCreated(eventId, productId, occurredAt);
        publishLikeCreated(eventId, productId, occurredAt);

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L));
        await().during(Duration.ofSeconds(1)).untilAsserted(() ->
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L));
    }

    @DisplayName("좋아요 취소 이벤트를 소비하면 product_metrics와 product_metrics_daily의 좋아요 수가 함께 감소하고 0 밑으로 내려가지 않는다.")
    @Test
    void decreasesLikeCount_flooredAtZero_inBothAggregateAndDailyMetrics_whenLikeDeletedEventIsConsumed() {
        // arrange
        Long productId = 103L;
        ZonedDateTime occurredAt = ZonedDateTime.now();
        LocalDate metricDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();
        publishLikeCreated(UUID.randomUUID().toString(), productId, occurredAt);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L));

        // act
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(productId),
            envelope(UUID.randomUUID().toString(), "LIKE_DELETED", productId, occurredAt,
                Map.of("userId", 1L, "productId", productId)));
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(productId),
            envelope(UUID.randomUUID().toString(), "LIKE_DELETED", productId, occurredAt,
                Map.of("userId", 2L, "productId", productId)));

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(metricsOf(productId).getLikeCount()).isEqualTo(0L),
            () -> assertThat(dailyMetricsOf(productId, metricDate).getLikeCount()).isEqualTo(0L)
        ));
    }

    @DisplayName("상품 조회 이벤트를 소비하면 product_metrics와 product_metrics_daily의 조회 수가 함께 증가한다.")
    @Test
    void increasesViewCount_inBothAggregateAndDailyMetrics_whenProductViewedEventIsConsumed() {
        // arrange
        Long productId = 104L;
        ZonedDateTime occurredAt = ZonedDateTime.now();
        LocalDate metricDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

        // act
        kafkaTemplate.send(CATALOG_EVENTS_TOPIC, String.valueOf(productId),
            envelope(UUID.randomUUID().toString(), "PRODUCT_VIEWED", productId, occurredAt,
                Map.of("productId", productId)));

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(metricsOf(productId).getViewCount()).isEqualTo(1L),
            () -> assertThat(dailyMetricsOf(productId, metricDate).getViewCount()).isEqualTo(1L)
        ));
    }

    @DisplayName("주문 생성 이벤트를 소비하면 product_metrics와 product_metrics_daily의 항목별 판매량이 함께 증가하고, 일별 판매 금액은 단가×수량 합으로 쌓인다.")
    @Test
    void increasesSalesCountAndAmount_inBothAggregateAndDailyMetrics_whenOrderCreatedEventIsConsumed() {
        // arrange
        Long firstProductId = 105L;
        Long secondProductId = 106L;
        int firstPrice = 10_000;
        int secondPrice = 6_000;
        ZonedDateTime occurredAt = ZonedDateTime.now();
        LocalDate metricDate = occurredAt.withZoneSameInstant(SEOUL_ZONE).toLocalDate();

        // act
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, "1",
            envelope(UUID.randomUUID().toString(), "ORDER_CREATED", 1L, occurredAt,
                Map.of(
                    "orderId", 1L,
                    "userId", 1L,
                    "finalAmount", 78_000,
                    "items", List.of(
                        Map.of("productId", firstProductId, "quantity", 2, "price", firstPrice),
                        Map.of("productId", secondProductId, "quantity", 3, "price", secondPrice)
                    )
                )));

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(metricsOf(firstProductId).getSalesCount()).isEqualTo(2L),
            () -> assertThat(metricsOf(secondProductId).getSalesCount()).isEqualTo(3L),
            () -> assertThat(dailyMetricsOf(firstProductId, metricDate).getSalesCount()).isEqualTo(2L),
            () -> assertThat(dailyMetricsOf(secondProductId, metricDate).getSalesCount()).isEqualTo(3L),
            () -> assertThat(dailyMetricsOf(firstProductId, metricDate).getSalesAmount()).isEqualTo(firstPrice * 2L),
            () -> assertThat(dailyMetricsOf(secondProductId, metricDate).getSalesAmount()).isEqualTo(secondPrice * 3L)
        ));
    }

    @DisplayName("발생 시각이 다른 날짜인 이벤트는 product_metrics_daily에 서로 다른 행으로 쌓인다.")
    @Test
    void createsSeparateDailyRows_whenEventsOccurOnDifferentDates() {
        // arrange
        Long productId = 108L;
        ZonedDateTime today = ZonedDateTime.now(SEOUL_ZONE);
        ZonedDateTime yesterday = today.minusDays(1);
        LocalDate todayMetricDate = today.toLocalDate();
        LocalDate yesterdayMetricDate = yesterday.toLocalDate();

        // act
        publishLikeCreated(UUID.randomUUID().toString(), productId, yesterday);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(dailyMetricsOf(productId, yesterdayMetricDate).getLikeCount()).isEqualTo(1L));
        publishLikeCreated(UUID.randomUUID().toString(), productId, today);

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(dailyMetricsOf(productId, yesterdayMetricDate).getLikeCount()).isEqualTo(1L),
            () -> assertThat(dailyMetricsOf(productId, todayMetricDate).getLikeCount()).isEqualTo(1L),
            () -> assertThat(metricsOf(productId).getLikeCount()).isEqualTo(2L)
        ));
    }

    @DisplayName("순서가 뒤바뀐 과거 이벤트가 와도 델타는 반영되고 last_event_at은 최신 시각을 유지한다.")
    @Test
    void keepsLatestWatermark_whileApplyingDelta_whenStaleEventArrives() {
        // arrange
        Long productId = 107L;
        ZonedDateTime latest = ZonedDateTime.now();
        ZonedDateTime stale = latest.minusHours(1);
        publishLikeCreated(UUID.randomUUID().toString(), productId, latest);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(metricsOf(productId).getLikeCount()).isEqualTo(1L));

        // act
        publishLikeCreated(UUID.randomUUID().toString(), productId, stale);

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(metricsOf(productId).getLikeCount()).isEqualTo(2L),
            () -> assertThat(metricsOf(productId).getLastEventAt()).isAfter(stale.plusMinutes(30))
        ));
    }
}
