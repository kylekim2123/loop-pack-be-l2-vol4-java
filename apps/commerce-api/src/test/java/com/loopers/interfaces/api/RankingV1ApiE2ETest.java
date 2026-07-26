package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.support.ranking.RankingKeyGenerator;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/rankings";
    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RANKING_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build());
    }

    private void seedRanking(LocalDate rankingDate, Long productId, double score) {
        masterRedisTemplate.opsForZSet().add(RankingKeyGenerator.generate(rankingDate), String.valueOf(productId), score);
    }

    private void seedMvWeekly(String periodKey, int rank, long productId) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly (period_key, `rank`, product_id) VALUES (?, ?, ?)",
            periodKey, rank, productId);
    }

    private void seedMvMonthly(String periodKey, int rank, long productId) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_monthly (period_key, `rank`, product_id) VALUES (?, ?, ?)",
            periodKey, rank, productId);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(null);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> getRankings(String queryString) {
        return testRestTemplate.exchange(ENDPOINT + queryString, HttpMethod.GET, guestGet(), MAP_RESPONSE);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("랭킹 페이지 조회 - GET /api/v1/rankings")
    @Nested
    class ReadRankings {

        @DisplayName("점수 내림차순으로 정렬되고, 각 항목은 순위 번호와 상품·브랜드 정보를 포함한다.")
        @Test
        void returnsOk_withRankOrderAndProductInfo() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel first = saveProduct(brand.getId(), "1위 상품", 39_000, 5);
            ProductModel second = saveProduct(brand.getId(), "2위 상품", 29_000, 5);
            ProductModel third = saveProduct(brand.getId(), "3위 상품", 19_000, 5);
            seedRanking(today, first.getId(), 300);
            seedRanking(today, second.getId(), 200);
            seedRanking(today, third.getId(), 100);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?page=1&size=20");

            // assert
            List<Map<String, Object>> content = contentOf(response);
            Map<String, Object> topItem = content.get(0);
            Map<?, ?> topItemBrand = (Map<?, ?>) topItem.get("brand");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(content)
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(first.getId(), second.getId(), third.getId()),
                () -> assertThat(content)
                    .extracting(item -> ((Number) item.get("rank")).longValue())
                    .containsExactly(1L, 2L, 3L),
                () -> assertThat(topItem.get("name")).isEqualTo("1위 상품"),
                () -> assertThat(((Number) topItem.get("price")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) topItemBrand.get("brandId")).longValue()).isEqualTo(brand.getId()),
                () -> assertThat(topItemBrand.get("name")).isEqualTo("감성 브랜드")
            );
        }

        @DisplayName("size만큼 페이지를 나누어 조회하면, 다음 페이지에는 남은 순위의 상품만 순위 번호와 함께 반환된다.")
        @Test
        void returnsOk_withNextPageAndCorrectRank() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel first = saveProduct(brand.getId(), "1위 상품", 39_000, 5);
            ProductModel second = saveProduct(brand.getId(), "2위 상품", 29_000, 5);
            ProductModel third = saveProduct(brand.getId(), "3위 상품", 19_000, 5);
            seedRanking(today, first.getId(), 300);
            seedRanking(today, second.getId(), 200);
            seedRanking(today, third.getId(), 100);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?page=2&size=2");

            // assert
            List<Map<String, Object>> content = contentOf(response);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(content).hasSize(1),
                () -> assertThat(((Number) content.get(0).get("productId")).longValue()).isEqualTo(third.getId()),
                () -> assertThat(((Number) content.get(0).get("rank")).longValue()).isEqualTo(3L),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(3L)
            );
        }

        @DisplayName("page가 0 이하이면 1페이지로 보정되어 200 OK와 함께 1페이지와 동일한 결과가 반환된다.")
        @Test
        void returnsOk_withFirstPageContent_whenPageIsZero() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel first = saveProduct(brand.getId(), "1위 상품", 39_000, 5);
            ProductModel second = saveProduct(brand.getId(), "2위 상품", 29_000, 5);
            seedRanking(today, first.getId(), 300);
            seedRanking(today, second.getId(), 200);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> zeroPageResponse = getRankings("?page=0&size=20");
            ResponseEntity<ApiResponse<Map<String, Object>>> firstPageResponse = getRankings("?page=1&size=20");

            // assert
            assertAll(
                () -> assertThat(zeroPageResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(((Number) zeroPageResponse.getBody().data().get("page")).intValue()).isEqualTo(1),
                () -> assertThat(contentOf(zeroPageResponse))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(first.getId(), second.getId()),
                () -> assertThat(contentOf(zeroPageResponse))
                    .extracting(item -> ((Number) item.get("rank")).longValue())
                    .containsExactly(1L, 2L),
                () -> assertThat(contentOf(zeroPageResponse)).isEqualTo(contentOf(firstPageResponse))
            );
        }

        @DisplayName("해당 날짜의 랭킹판이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenRankingKeyIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?page=1&size=20");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty(),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(0L)
            );
        }

        @DisplayName("date 파라미터로 지정한 날짜의 랭킹판만 조회되고, 생략하면 오늘 날짜의 랭킹판이 조회된다.")
        @Test
        void returnsOk_withDateIsolatedRanking() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            LocalDate yesterday = today.minusDays(1);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel todayProduct = saveProduct(brand.getId(), "오늘의 상품", 39_000, 5);
            ProductModel yesterdayProduct = saveProduct(brand.getId(), "어제의 상품", 29_000, 5);
            seedRanking(today, todayProduct.getId(), 100);
            seedRanking(yesterday, yesterdayProduct.getId(), 100);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> todayResponse = getRankings("?page=1&size=20");
            ResponseEntity<ApiResponse<Map<String, Object>>> yesterdayResponse =
                getRankings("?date=" + RANKING_DATE_FORMATTER.format(yesterday) + "&page=1&size=20");

            // assert
            assertAll(
                () -> assertThat(contentOf(todayResponse))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(todayProduct.getId()),
                () -> assertThat(contentOf(yesterdayResponse))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(yesterdayProduct.getId())
            );
        }

        @DisplayName("date 형식이 uuuuMMdd가 아니면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenDateFormatIsInvalid() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?date=2026-07-17&page=1&size=20");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("랭킹판에는 있으나 삭제되어 조회되지 않는 상품은 응답에서 제외되고, 남은 상품의 순위는 ZSET 기준을 유지한다.")
        @Test
        void excludesDeletedProduct_fromContent() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel survivingProduct = saveProduct(brand.getId(), "살아있는 상품", 39_000, 5);
            ProductModel deletedProduct = saveProduct(brand.getId(), "삭제된 상품", 29_000, 5);
            deletedProduct.delete();
            productJpaRepository.saveAndFlush(deletedProduct);
            seedRanking(today, deletedProduct.getId(), 300);
            seedRanking(today, survivingProduct.getId(), 200);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?page=1&size=20");

            // assert
            assertAll(
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(((Number) contentOf(response).get(0).get("productId")).longValue())
                    .isEqualTo(survivingProduct.getId()),
                () -> assertThat(((Number) contentOf(response).get(0).get("rank")).longValue())
                    .isEqualTo(2L)
            );
        }

        @DisplayName("period 없이 조회하면 응답에 DAILY 기간(그 날짜)이 명시된다.")
        @Test
        void returnsOk_withDailyPeriodMetadata_whenPeriodOmitted() {
            // arrange
            LocalDate today = LocalDate.now(RANKING_ZONE);
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "오늘의 상품", 39_000, 5);
            seedRanking(today, product.getId(), 100);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?page=1&size=20");

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(data.get("period")).isEqualTo("DAILY"),
                () -> assertThat(data.get("periodKey")).isEqualTo(RANKING_DATE_FORMATTER.format(today)),
                () -> assertThat(data.get("periodStart")).isEqualTo(today.toString()),
                () -> assertThat(data.get("periodEnd")).isEqualTo(today.toString())
            );
        }
    }

    @DisplayName("주간·월간 랭킹 조회 - GET /api/v1/rankings?period=")
    @Nested
    class ReadPeriodRankings {

        @DisplayName("period=WEEKLY이면 그 날짜가 속한 주의 MV에서 순위·상품 정보와 기간을 조회한다.")
        @Test
        void returnsOk_withWeeklyRankingFromMv() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel first = saveProduct(brand.getId(), "주간 1위", 39_000, 5);
            ProductModel second = saveProduct(brand.getId(), "주간 2위", 29_000, 5);
            seedMvWeekly("2026-W30", 1, first.getId());
            seedMvWeekly("2026-W30", 2, second.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?period=WEEKLY&date=20260722&page=1&size=20");

            // assert
            Map<String, Object> data = response.getBody().data();
            List<Map<String, Object>> content = contentOf(response);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("period")).isEqualTo("WEEKLY"),
                () -> assertThat(data.get("periodKey")).isEqualTo("2026-W30"),
                () -> assertThat(data.get("periodStart")).isEqualTo("2026-07-20"),
                () -> assertThat(data.get("periodEnd")).isEqualTo("2026-07-26"),
                () -> assertThat(content)
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(first.getId(), second.getId()),
                () -> assertThat(content)
                    .extracting(item -> ((Number) item.get("rank")).longValue())
                    .containsExactly(1L, 2L),
                () -> assertThat(content.get(0).get("name")).isEqualTo("주간 1위")
            );
        }

        @DisplayName("period=MONTHLY이면 그 날짜가 속한 달의 MV에서 순위·상품 정보와 기간을 조회한다.")
        @Test
        void returnsOk_withMonthlyRankingFromMv() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "월간 1위", 39_000, 5);
            seedMvMonthly("2026-07", 1, product.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?period=MONTHLY&date=20260722&page=1&size=20");

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.get("period")).isEqualTo("MONTHLY"),
                () -> assertThat(data.get("periodKey")).isEqualTo("2026-07"),
                () -> assertThat(data.get("periodStart")).isEqualTo("2026-07-01"),
                () -> assertThat(data.get("periodEnd")).isEqualTo("2026-07-31"),
                () -> assertThat(contentOf(response))
                    .extracting(item -> ((Number) item.get("productId")).longValue())
                    .containsExactly(product.getId())
            );
        }

        @DisplayName("같은 주에 속한 다른 날짜로 조회하면 동일한 주간 랭킹이 반환된다.")
        @Test
        void returnsOk_withSameWeeklyRanking_forDifferentDatesInSameWeek() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "주간 상품", 39_000, 5);
            seedMvWeekly("2026-W30", 1, product.getId());

            // act (2026-07-20 월요일, 2026-07-26 일요일 — 같은 주)
            ResponseEntity<ApiResponse<Map<String, Object>>> mondayResponse = getRankings("?period=WEEKLY&date=20260720");
            ResponseEntity<ApiResponse<Map<String, Object>>> sundayResponse = getRankings("?period=WEEKLY&date=20260726");

            // assert
            assertAll(
                () -> assertThat(mondayResponse.getBody().data().get("periodKey")).isEqualTo("2026-W30"),
                () -> assertThat(sundayResponse.getBody().data().get("periodKey")).isEqualTo("2026-W30"),
                () -> assertThat(contentOf(mondayResponse)).isEqualTo(contentOf(sundayResponse))
            );
        }

        @DisplayName("아직 발행되지 않은 주간 랭킹을 조회하면 200 OK와 함께 빈 목록·기간이 반환된다.")
        @Test
        void returnsOk_withEmptyContent_whenWeeklyNotPublished() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?period=WEEKLY&date=20260722");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty(),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(0L),
                () -> assertThat(response.getBody().data().get("periodKey")).isEqualTo("2026-W30")
            );
        }

        @DisplayName("주간 랭킹을 페이지로 나누면 다음 페이지에 MV 저장 순위 그대로 남은 상품이 반환된다.")
        @Test
        void returnsOk_withNextPage_usingStoredRank() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel first = saveProduct(brand.getId(), "주간 1위", 39_000, 5);
            ProductModel second = saveProduct(brand.getId(), "주간 2위", 29_000, 5);
            ProductModel third = saveProduct(brand.getId(), "주간 3위", 19_000, 5);
            seedMvWeekly("2026-W30", 1, first.getId());
            seedMvWeekly("2026-W30", 2, second.getId());
            seedMvWeekly("2026-W30", 3, third.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?period=WEEKLY&date=20260722&page=2&size=2");

            // assert
            List<Map<String, Object>> content = contentOf(response);
            assertAll(
                () -> assertThat(content).hasSize(1),
                () -> assertThat(((Number) content.get(0).get("productId")).longValue()).isEqualTo(third.getId()),
                () -> assertThat(((Number) content.get(0).get("rank")).longValue()).isEqualTo(3L),
                () -> assertThat(((Number) response.getBody().data().get("totalElements")).longValue()).isEqualTo(3L)
            );
        }

        @DisplayName("period 값이 DAILY·WEEKLY·MONTHLY가 아니면 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenPeriodIsInvalid() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = getRankings("?period=YEARLY&date=20260722");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }
}
