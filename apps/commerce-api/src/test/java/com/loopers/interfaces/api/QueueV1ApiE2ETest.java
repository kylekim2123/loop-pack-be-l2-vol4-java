package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private void saveUser(String loginId) {
        userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private HttpEntity<Void> memberRequest(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestRequest() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> enter(String loginId) {
        return testRestTemplate.exchange(ENTER_ENDPOINT, HttpMethod.POST, memberRequest(loginId), MAP_RESPONSE);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> readPosition(String loginId) {
        return testRestTemplate.exchange(POSITION_ENDPOINT, HttpMethod.GET, memberRequest(loginId), MAP_RESPONSE);
    }

    private long positionOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return ((Number) response.getBody().data().get("position")).longValue();
    }

    private long totalWaitingOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return ((Number) response.getBody().data().get("totalWaiting")).longValue();
    }

    @DisplayName("대기열 진입 - POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("처음 진입하면, 201 Created와 함께 순번 1과 전체 대기 인원 1이 반환된다.")
        @Test
        void returnsFirstPosition_whenEntersFirst() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = enter("kylekim");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("position", "totalWaiting"),
                () -> assertThat(positionOf(response)).isEqualTo(1),
                () -> assertThat(totalWaitingOf(response)).isEqualTo(1)
            );
        }

        @DisplayName("먼저 진입한 유저가 있으면, 나중에 진입한 유저는 뒷 순번을 받는다.")
        @Test
        void returnsLaterPosition_whenEntersAfterAnother() {
            // arrange
            saveUser("firstUser");
            saveUser("secondUser");
            enter("firstUser");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = enter("secondUser");

            // assert
            assertAll(
                () -> assertThat(positionOf(response)).isEqualTo(2),
                () -> assertThat(totalWaitingOf(response)).isEqualTo(2)
            );
        }

        @DisplayName("이미 대기 중인 유저가 다시 진입하면, 순번이 갱신되어 맨 뒤로 밀리고 전체 인원은 그대로다.")
        @Test
        void movesToBack_whenAlreadyWaitingUserReenters() {
            // arrange
            saveUser("firstUser");
            saveUser("secondUser");
            enter("firstUser");
            enter("secondUser");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = enter("firstUser");

            // assert
            assertAll(
                () -> assertThat(positionOf(response)).isEqualTo(2),
                () -> assertThat(totalWaitingOf(response)).isEqualTo(2),
                () -> assertThat(positionOf(readPosition("secondUser"))).isEqualTo(1)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENTER_ENDPOINT, HttpMethod.POST, guestRequest(), MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getStatus()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }
    }

    @DisplayName("순번 조회 - GET /api/v1/queue/position")
    @Nested
    class ReadPosition {

        @DisplayName("대기 중인 유저가 조회하면, 200 OK와 함께 현재 순번과 전체 대기 인원이 반환된다.")
        @Test
        void returnsPosition_whenWaiting() {
            // arrange
            saveUser("kylekim");
            enter("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = readPosition("kylekim");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data()).containsOnlyKeys("position", "totalWaiting"),
                () -> assertThat(positionOf(response)).isEqualTo(1),
                () -> assertThat(totalWaitingOf(response)).isEqualTo(1)
            );
        }

        @DisplayName("대기열에 없는 유저가 조회하면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenNotInQueue() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = readPosition("kylekim");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }
}
