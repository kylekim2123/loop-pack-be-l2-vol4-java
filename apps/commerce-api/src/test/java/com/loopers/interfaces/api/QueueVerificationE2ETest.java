package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.application.queue.EntryTokenScheduler;
import com.loopers.application.queue.QueueProperties;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueVerificationE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";
    private static final String ORDER_ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String ENTRY_TOKEN_KEY_PREFIX = "entry-token:";
    private static final String WAITING_QUEUE_KEY = "waiting-queue";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private EntryTokenScheduler entryTokenScheduler;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private Long saveProduct(int stock) {
        BrandModel brand = brandJpaRepository.save(BrandModel.builder()
            .rawName("감성 브랜드")
            .rawDescription("감성을 담은 브랜드")
            .build());

        return productJpaRepository.save(ProductModel.builder()
            .brandId(brand.getId())
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(stock)
            .build()).getId();
    }

    private void seedEntryToken(Long userId, String token, Duration ttl) {
        masterRedisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, token, ttl);
    }

    private void seedWaiting(long userId, double score) {
        masterRedisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, String.valueOf(userId), score);
    }

    private String storedEntryToken(Long userId) {
        return masterRedisTemplate.opsForValue().get(ENTRY_TOKEN_KEY_PREFIX + userId);
    }

    private boolean hasEntryToken(long userId) {
        return Boolean.TRUE.equals(masterRedisTemplate.hasKey(ENTRY_TOKEN_KEY_PREFIX + userId));
    }

    private HttpEntity<Void> memberRequest(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> enter(String loginId) {
        return testRestTemplate.exchange(ENTER_ENDPOINT, HttpMethod.POST, memberRequest(loginId), MAP_RESPONSE);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> readPosition(String loginId) {
        return testRestTemplate.exchange(POSITION_ENDPOINT, HttpMethod.GET, memberRequest(loginId), MAP_RESPONSE);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> order(String loginId, String entryToken, Long productId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);
        headers.add(ENTRY_TOKEN_HEADER, entryToken);
        OrderV1Dto.CreateRequest body = new OrderV1Dto.CreateRequest(
            List.of(new OrderV1Dto.OrderItemRequest(productId, 1)), null
        );

        return testRestTemplate.exchange(ORDER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, headers), MAP_RESPONSE);
    }

    private long positionOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return ((Number) response.getBody().data().get("position")).longValue();
    }

    private void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    task.run();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @DisplayName("동시 진입 - 여러 유저가 한꺼번에 줄을 설 때,")
    @Nested
    class ConcurrentEnter {

        @DisplayName("전원이 수용되고, 진입이 끝난 뒤 각자의 순번은 1부터 N까지 정확히 한 번씩 배정된다.")
        @Test
        void assignsUniquePositions_whenUsersEnterConcurrently() throws InterruptedException {
            // arrange (10명이 동시에 진입)
            int userCount = 10;
            List<String> loginIds = LongStream.rangeClosed(1, userCount)
                .mapToObj(index -> "user" + index)
                .toList();
            loginIds.forEach(QueueVerificationE2ETest.this::saveUser);
            ConcurrentLinkedQueue<String> remainingLoginIds = new ConcurrentLinkedQueue<>(loginIds);
            ConcurrentLinkedQueue<HttpStatusCode> statuses = new ConcurrentLinkedQueue<>();

            // act
            runConcurrently(userCount, () -> statuses.add(enter(remainingLoginIds.poll()).getStatusCode()));

            // assert (응답 중 순번은 스냅샷이라 중복될 수 있으므로, 완료 후 조회한 순번으로 유일성 검증)
            List<Long> finalPositions = loginIds.stream()
                .map(loginId -> positionOf(readPosition(loginId)))
                .toList();
            assertAll(
                () -> assertThat(statuses).allMatch(status -> status.equals(HttpStatus.CREATED)),
                () -> assertThat(queueRepository.count()).isEqualTo(userCount),
                () -> assertThat(finalPositions).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, userCount).boxed().toList())
            );
        }

        @DisplayName("같은 유저가 동시에 여러 번 진입해도, 대기열에는 한 번만 선다.")
        @Test
        void entersOnlyOnce_whenSameUserEntersConcurrently() throws InterruptedException {
            // arrange (같은 유저가 10번 동시에 진입)
            saveUser("kylekim");
            int attemptCount = 10;

            // act
            runConcurrently(attemptCount, () -> enter("kylekim"));

            // assert
            assertAll(
                () -> assertThat(queueRepository.count()).isEqualTo(1),
                () -> assertThat(positionOf(readPosition("kylekim"))).isEqualTo(1)
            );
        }
    }

    @DisplayName("입장권 만료 - 발급받고 시간이 지나면,")
    @Nested
    class EntryTokenExpiry {

        @DisplayName("만료된 입장권으로 주문하면 차단되고, 주문은 생성되지 않는다.")
        @Test
        void rejectsOrder_whenEntryTokenExpires() {
            // arrange (1초짜리 입장권을 심고 만료를 기다림)
            UserModel user = saveUser("kylekim");
            Long productId = saveProduct(10);
            seedEntryToken(user.getId(), "expiring-token", Duration.ofSeconds(1));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(hasEntryToken(user.getId())).isFalse());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = order("kylekim", "expiring-token", productId);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("더블클릭 - 같은 입장권으로 동시에 두 번 주문하면,")
    @Nested
    class DoubleClick {

        @DisplayName("딱 한 번만 통과해 주문은 1건만 생성되고, 입장권은 소비된다.")
        @Test
        void passesExactlyOnce_whenSameTokenOrderedConcurrently() throws InterruptedException {
            // arrange
            UserModel user = saveUser("kylekim");
            Long productId = saveProduct(10);
            seedEntryToken(user.getId(), "valid-token", Duration.ofMinutes(5));
            ConcurrentLinkedQueue<HttpStatusCode> statuses = new ConcurrentLinkedQueue<>();

            // act
            runConcurrently(2, () -> statuses.add(order("kylekim", "valid-token", productId).getStatusCode()));

            // assert
            assertAll(
                () -> assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.FORBIDDEN),
                () -> assertThat(orderJpaRepository.findAll()).hasSize(1),
                () -> assertThat(storedEntryToken(user.getId())).isNull()
            );
        }
    }

    @DisplayName("처리량 초과 - 배치 크기보다 많은 인원이 몰리면,")
    @Nested
    class ThroughputOverflow {

        @DisplayName("한 주기엔 앞에서 배치 크기만큼만 발급되고, 초과분은 대기열에 남아 다음 주기에 순서대로 발급된다.")
        @Test
        void drainsAtBatchRate_whenWaitingExceedsBatchSize() {
            // arrange (배치 2번 + 마지막 자투리(배치 미만)로 정확히 3주기에 소진되는 인원)
            int batchSize = queueProperties.batchSize();
            int lastBatchRemainder = batchSize - 1;
            int waitingCount = batchSize * 2 + lastBatchRemainder;
            LongStream.rangeClosed(1, waitingCount).forEach(userId -> seedWaiting(userId, userId));

            // act & assert (주기마다 배치 크기만큼만 줄어드는지 단계별 확인)
            entryTokenScheduler.issueEntryTokens();
            assertAll(
                () -> assertThat(queueRepository.count()).isEqualTo(waitingCount - batchSize),
                () -> assertThat(hasEntryToken(1)).isTrue(),
                () -> assertThat(hasEntryToken(batchSize)).isTrue(),
                () -> assertThat(hasEntryToken(batchSize + 1)).isFalse()
            );

            entryTokenScheduler.issueEntryTokens();
            assertAll(
                () -> assertThat(queueRepository.count()).isEqualTo(waitingCount - batchSize * 2),
                () -> assertThat(hasEntryToken(batchSize * 2)).isTrue(),
                () -> assertThat(hasEntryToken(batchSize * 2 + 1)).isFalse()
            );

            entryTokenScheduler.issueEntryTokens();
            assertAll(
                () -> assertThat(queueRepository.count()).isZero(),
                () -> assertThat(hasEntryToken(waitingCount)).isTrue()
            );
        }
    }

    @DisplayName("관통 플로우 - 진입부터 주문까지,")
    @Nested
    class FullFlow {

        @DisplayName("진입 → 발급 → 순번 조회로 입장권 수령 → 주문 성공 → 입장권 소비 → 조회하면 404로 이어진다.")
        @Test
        void completesOrder_throughQueueEntryAndTokenIssuance() {
            // arrange
            UserModel user = saveUser("kylekim");
            Long productId = saveProduct(10);

            // act & assert (① 진입: 순번 1)
            ResponseEntity<ApiResponse<Map<String, Object>>> enterResponse = enter("kylekim");
            assertAll(
                () -> assertThat(enterResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(positionOf(enterResponse)).isEqualTo(1)
            );

            // act & assert (② 스케줄러 발급 후 순번 조회: 순번 0 + 입장권 수령, 줄에서 빠짐)
            entryTokenScheduler.issueEntryTokens();
            ResponseEntity<ApiResponse<Map<String, Object>>> positionResponse = readPosition("kylekim");
            String entryToken = (String) positionResponse.getBody().data().get("entryToken");
            assertAll(
                () -> assertThat(positionResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(positionOf(positionResponse)).isZero(),
                () -> assertThat(entryToken).isNotBlank(),
                () -> assertThat(queueRepository.count()).isZero()
            );

            // act & assert (③ 수령한 입장권으로 주문: 성공 + 입장권 소비)
            ResponseEntity<ApiResponse<Map<String, Object>>> orderResponse = order("kylekim", entryToken, productId);
            assertAll(
                () -> assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(orderResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(orderJpaRepository.findAll()).hasSize(1),
                () -> assertThat(storedEntryToken(user.getId())).isNull()
            );

            // act & assert (④ 주문 뒤 순번 조회: 대기열에도 입장권에도 없어 404)
            ResponseEntity<ApiResponse<Map<String, Object>>> afterOrderResponse = readPosition("kylekim");
            assertAll(
                () -> assertThat(afterOrderResponse.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                () -> assertThat(afterOrderResponse.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(afterOrderResponse.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }
}
