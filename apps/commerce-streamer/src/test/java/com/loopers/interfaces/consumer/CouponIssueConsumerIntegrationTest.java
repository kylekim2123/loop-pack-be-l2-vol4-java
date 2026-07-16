package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.application.event.CouponIssueRequestedEventParser;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class CouponIssueConsumerIntegrationTest {

    private static final String COUPON_ISSUE_REQUESTS_TOPIC = "coupon-issue-requests";

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private CouponIssueProcessor couponIssueProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS coupons (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                discount_type VARCHAR(20) NOT NULL,
                discount_value INT NOT NULL,
                min_order_amount INT NOT NULL,
                expired_at DATETIME(6) NOT NULL,
                max_quantity INT NULL,
                issued_count INT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS user_coupons (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                coupon_id BIGINT NOT NULL,
                name VARCHAR(100) NOT NULL,
                discount_type VARCHAR(20) NOT NULL,
                discount_value INT NOT NULL,
                min_order_amount INT NOT NULL,
                expired_at DATETIME(6) NOT NULL,
                used_at DATETIME(6) NULL,
                version BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL,
                CONSTRAINT uk_user_coupons_user_id_coupon_id UNIQUE (user_id, coupon_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS coupon_issue_requests (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                coupon_id BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                reason VARCHAR(200) NULL,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.update("DELETE FROM user_coupons");
        jdbcTemplate.update("DELETE FROM coupon_issue_requests");
        jdbcTemplate.update("DELETE FROM coupons");
    }

    private Long saveCoupon(Integer maxQuantity, int issuedCount) {
        jdbcTemplate.update("""
            INSERT INTO coupons (name, discount_type, discount_value, min_order_amount, expired_at, max_quantity, issued_count)
            VALUES ('선착순 쿠폰', 'FIXED', 5000, 10000, DATE_ADD(NOW(6), INTERVAL 7 DAY), ?, ?)
            """, maxQuantity, issuedCount);

        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM coupons", Long.class);
    }

    private Long saveRequest(Long userId, Long couponId) {
        jdbcTemplate.update("INSERT INTO coupon_issue_requests (user_id, coupon_id, status) VALUES (?, ?, 'PENDING')", userId, couponId);

        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM coupon_issue_requests", Long.class);
    }

    private Map<String, Object> issueRequestedEnvelope(String eventId, Long requestId, Long userId, Long couponId) {
        return Map.of(
            "eventId", eventId,
            "eventType", "COUPON_ISSUE_REQUESTED",
            "aggregateId", String.valueOf(couponId),
            "occurredAt", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "data", Map.of("requestId", requestId, "userId", userId, "couponId", couponId)
        );
    }

    private CouponIssueRequestedEvent couponIssueRequestedEvent(Long requestId, Long userId, Long couponId) {
        try {
            return CouponIssueRequestedEventParser.parse(objectMapper.readTree(
                objectMapper.writeValueAsString(issueRequestedEnvelope(UUID.randomUUID().toString(), requestId, userId, couponId))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String requestStatusOf(Long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM coupon_issue_requests WHERE id = ?", String.class, requestId);
    }

    private long userCouponCountOf(Long couponId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_coupons WHERE coupon_id = ?", Long.class, couponId);
    }

    @DisplayName("발급 요청 이벤트를 소비하면 수량을 차감하고 발급 쿠폰을 저장한 뒤 요청을 SUCCESS로 전이한다.")
    @Test
    void issuesCoupon_andMarksSuccess_whenQuantityRemains() {
        // arrange
        Long couponId = saveCoupon(100, 0);
        Long requestId = saveRequest(1L, couponId);

        // act
        kafkaTemplate.send(COUPON_ISSUE_REQUESTS_TOPIC, String.valueOf(couponId),
            issueRequestedEnvelope(UUID.randomUUID().toString(), requestId, 1L, couponId));

        // assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertAll(
            () -> assertThat(requestStatusOf(requestId)).isEqualTo("SUCCESS"),
            () -> assertThat(userCouponCountOf(couponId)).isEqualTo(1L),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT issued_count FROM coupons WHERE id = ?", Long.class, couponId)).isEqualTo(1L)
        ));
    }

    @DisplayName("수량이 소진됐으면 발급하지 않고 요청을 FAILED로 전이한다.")
    @Test
    void marksFailed_whenQuantityIsSoldOut() {
        // arrange
        Long couponId = saveCoupon(1, 1);
        Long requestId = saveRequest(1L, couponId);

        // act
        couponIssueProcessor.process(couponIssueRequestedEvent(requestId, 1L, couponId));

        // assert
        assertAll(
            () -> assertThat(requestStatusOf(requestId)).isEqualTo("FAILED"),
            () -> assertThat(userCouponCountOf(couponId)).isZero(),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT issued_count FROM coupons WHERE id = ?", Long.class, couponId)).isEqualTo(1L)
        );
    }

    @DisplayName("이미 발급받은 유저의 요청은 수량 차감 없이 FAILED로 전이한다.")
    @Test
    void marksFailed_withoutDecrement_whenUserAlreadyIssued() {
        // arrange
        Long couponId = saveCoupon(100, 1);
        jdbcTemplate.update("""
            INSERT INTO user_coupons (user_id, coupon_id, name, discount_type, discount_value, min_order_amount, expired_at, version)
            VALUES (1, ?, '선착순 쿠폰', 'FIXED', 5000, 10000, DATE_ADD(NOW(6), INTERVAL 7 DAY), 0)
            """, couponId);
        Long requestId = saveRequest(1L, couponId);

        // act
        couponIssueProcessor.process(couponIssueRequestedEvent(requestId, 1L, couponId));

        // assert
        assertAll(
            () -> assertThat(requestStatusOf(requestId)).isEqualTo("FAILED"),
            () -> assertThat(userCouponCountOf(couponId)).isEqualTo(1L),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT issued_count FROM coupons WHERE id = ?", Long.class, couponId)).isEqualTo(1L)
        );
    }

    @DisplayName("같은 이벤트를 두 번 처리해도 발급은 한 번만 일어난다.")
    @Test
    void issuesOnlyOnce_whenSameEventIsProcessedTwice() {
        // arrange
        Long couponId = saveCoupon(100, 0);
        Long requestId = saveRequest(1L, couponId);
        CouponIssueRequestedEvent event = couponIssueRequestedEvent(requestId, 1L, couponId);

        // act
        couponIssueProcessor.process(event);
        couponIssueProcessor.process(event);

        // assert
        assertAll(
            () -> assertThat(userCouponCountOf(couponId)).isEqualTo(1L),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT issued_count FROM coupons WHERE id = ?", Long.class, couponId)).isEqualTo(1L)
        );
    }

    @DisplayName("수량 5장에 20명이 동시에 요청해도 발급 성공은 정확히 5건, 초과·중복은 0건이다.")
    @Test
    void issuesExactlyMaxQuantity_underConcurrentRequests() throws InterruptedException {
        // arrange
        int maxQuantity = 5;
        int requestCount = 20;
        Long couponId = saveCoupon(maxQuantity, 0);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        // act
        for (int i = 0; i < requestCount; i++) {
            long userId = i + 1;
            Long requestId = saveRequest(userId, couponId);
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    couponIssueProcessor.process(couponIssueRequestedEvent(requestId, userId, couponId));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);

        // assert
        assertAll(
            () -> assertThat(terminated).isTrue(),
            () -> assertThat(userCouponCountOf(couponId)).isEqualTo(maxQuantity),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT issued_count FROM coupons WHERE id = ?", Long.class, couponId)).isEqualTo((long) maxQuantity),
            () -> assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_issue_requests WHERE coupon_id = ? AND status = 'SUCCESS'", Long.class, couponId)).isEqualTo((long) maxQuantity),
            () -> assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_issue_requests WHERE coupon_id = ? AND status = 'FAILED'", Long.class, couponId)).isEqualTo((long) (requestCount - maxQuantity))
        );
    }
}
