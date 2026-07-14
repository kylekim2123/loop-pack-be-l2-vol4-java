package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
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
class EntryTokenGateE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String ENTRY_TOKEN_KEY_PREFIX = "entry-token:";
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

    private void seedEntryToken(Long userId, String token) {
        masterRedisTemplate.opsForValue().set(ENTRY_TOKEN_KEY_PREFIX + userId, token, Duration.ofMinutes(5));
    }

    private String storedEntryToken(Long userId) {
        return masterRedisTemplate.opsForValue().get(ENTRY_TOKEN_KEY_PREFIX + userId);
    }

    private HttpEntity<Object> orderRequest(String loginId, String entryToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);
        if (entryToken != null) {
            headers.add(ENTRY_TOKEN_HEADER, entryToken);
        }

        return new HttpEntity<>(body, headers);
    }

    private OrderV1Dto.CreateRequest orderBody(Long productId, int quantity) {
        return new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), null);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> order(String loginId, String entryToken, Object body) {
        return testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, orderRequest(loginId, entryToken, body), MAP_RESPONSE);
    }

    @DisplayName("입장권 헤더가 없으면, 403 Forbidden으로 막히고 주문은 생성되지 않는다.")
    @Test
    void returnsForbidden_whenEntryTokenIsMissing() {
        // arrange
        saveUser("kylekim");
        Long productId = saveProduct(50);

        // act
        ResponseEntity<ApiResponse<Map<String, Object>>> response = order("kylekim", null, orderBody(productId, 2));

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
            () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
            () -> assertThat(orderJpaRepository.findAll()).isEmpty()
        );
    }

    @DisplayName("입장권이 저장된 값과 다르면, 403 Forbidden으로 막히고 저장된 입장권은 지워지지 않는다.")
    @Test
    void returnsForbidden_andKeepsToken_whenEntryTokenMismatches() {
        // arrange
        UserModel user = saveUser("kylekim");
        Long productId = saveProduct(50);
        seedEntryToken(user.getId(), "real-token");

        // act
        ResponseEntity<ApiResponse<Map<String, Object>>> response = order("kylekim", "wrong-token", orderBody(productId, 2));

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
            () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode()),
            () -> assertThat(storedEntryToken(user.getId())).isEqualTo("real-token"),
            () -> assertThat(orderJpaRepository.findAll()).isEmpty()
        );
    }

    @DisplayName("올바른 입장권이면, 201 Created로 주문이 생성되고 입장권은 소비되어 사라진다.")
    @Test
    void returnsCreated_andConsumesToken_whenEntryTokenIsValid() {
        // arrange
        UserModel user = saveUser("kylekim");
        Long productId = saveProduct(50);
        seedEntryToken(user.getId(), "valid-token");

        // act
        ResponseEntity<ApiResponse<Map<String, Object>>> response = order("kylekim", "valid-token", orderBody(productId, 2));

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
            () -> assertThat(storedEntryToken(user.getId())).isNull(),
            () -> assertThat(orderJpaRepository.findAll()).hasSize(1)
        );
    }

    @DisplayName("입장권은 유효하지만 주문이 실패하면, 입장권이 되살아나 재시도할 수 있다.")
    @Test
    void restoresToken_whenOrderFailsAfterValidEntryToken() {
        // arrange
        UserModel user = saveUser("kylekim");
        Long productId = saveProduct(1);
        seedEntryToken(user.getId(), "valid-token");

        // act
        ResponseEntity<ApiResponse<Map<String, Object>>> response = order("kylekim", "valid-token", orderBody(productId, 5));

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
            () -> assertThat(storedEntryToken(user.getId())).isEqualTo("valid-token"),
            () -> assertThat(orderJpaRepository.findAll()).isEmpty()
        );
    }
}
