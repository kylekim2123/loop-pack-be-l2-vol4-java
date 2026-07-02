package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZonedDateTime;
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

import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private String issueEndpoint(Long couponId) {
        return "/api/v1/coupons/" + couponId + "/issue";
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

    private CouponModel saveCoupon() {
        return couponJpaRepository.save(CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build());
    }

    private CouponModel saveExpiredCoupon() {
        ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);

        return couponJpaRepository.save(CouponModel.builder()
            .rawName("만료 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(pastExpiredAt)
            .now(pastExpiredAt.minusDays(1))
            .build());
    }

    private HttpEntity<Void> memberPost(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestPost() {
        return new HttpEntity<>(new HttpHeaders());
    }

    @DisplayName("쿠폰 발급 요청 접수 - POST /api/v1/coupons/{couponId}/issue")
    @Nested
    class CreateCouponIssueRequest {

        @DisplayName("정상 요청이면, 202 Accepted와 함께 requestId가 반환되고 PENDING 요청이 저장된다.")
        @Test
        void returnsAccepted_andPersistsPendingRequest() {
            // arrange
            saveUser("kylekim");
            CouponModel coupon = saveCoupon();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                issueEndpoint(coupon.getId()),
                HttpMethod.POST,
                memberPost("kylekim"),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            Long requestId = Long.valueOf(String.valueOf(data.get("requestId")));
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).containsOnlyKeys("requestId", "status"),
                () -> assertThat(data.get("status")).isEqualTo(CouponIssueRequestStatus.PENDING.name()),
                () -> assertThat(couponIssueRequestJpaRepository.findById(requestId)).isPresent()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // arrange
            CouponModel coupon = saveCoupon();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                issueEndpoint(coupon.getId()),
                HttpMethod.POST,
                guestPost(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }

        @DisplayName("대상 템플릿이 존재하지 않으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTemplateIsAbsent() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                issueEndpoint(99999L),
                HttpMethod.POST,
                memberPost("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("대상 템플릿이 삭제됐으면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenTemplateIsDeleted() {
            // arrange
            saveUser("kylekim");
            CouponModel coupon = saveCoupon();
            coupon.delete();
            couponJpaRepository.saveAndFlush(coupon);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                issueEndpoint(coupon.getId()),
                HttpMethod.POST,
                memberPost("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("대상 템플릿이 만료됐으면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenTemplateIsExpired() {
            // arrange
            saveUser("kylekim");
            CouponModel coupon = saveExpiredCoupon();

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                issueEndpoint(coupon.getId()),
                HttpMethod.POST,
                memberPost("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode())
            );
        }
    }
}
