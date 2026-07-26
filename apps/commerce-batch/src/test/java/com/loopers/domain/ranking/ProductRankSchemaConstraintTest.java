package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class ProductRankSchemaConstraintTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("DELETE FROM product_rank_staging");
    }

    private void insertMvRow(String table, String periodKey, int rank, long productId) {
        jdbcTemplate.update(
            "INSERT INTO " + table + " (period_key, period_start, period_end, `rank`, product_id, score, "
                + "like_count, sales_count, view_count, sales_amount, created_at, updated_at) "
                + "VALUES (?, '2026-07-20', '2026-07-26', ?, ?, 1.0, 0, 0, 0, 0, NOW(6), NOW(6))",
            periodKey, rank, productId
        );
    }

    private void insertStagingRow(String periodType, String periodKey, long productId) {
        jdbcTemplate.update(
            "INSERT INTO product_rank_staging (period_type, period_key, product_id, score, "
                + "like_count, sales_count, view_count, sales_amount, created_at, updated_at) "
                + "VALUES (?, ?, ?, 1.0, 0, 0, 0, 0, NOW(6), NOW(6))",
            periodType, periodKey, productId
        );
    }

    @Nested
    @DisplayName("주간 MV 테이블은")
    class WeeklyMv {

        @Test
        @DisplayName("같은 기간에 같은 상품이 두 번 들어오면 유니크 제약으로 거부한다.")
        void rejectsDuplicateProductInSamePeriod() {
            // arrange
            insertMvRow("mv_product_rank_weekly", "2026-W30", 1, 100L);

            // act & assert
            assertThatThrownBy(() -> insertMvRow("mv_product_rank_weekly", "2026-W30", 2, 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 기간에 같은 순위가 두 번 들어오면 유니크 제약으로 거부한다.")
        void rejectsDuplicateRankInSamePeriod() {
            // arrange
            insertMvRow("mv_product_rank_weekly", "2026-W30", 1, 100L);

            // act & assert
            assertThatThrownBy(() -> insertMvRow("mv_product_rank_weekly", "2026-W30", 1, 200L))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("기간이 다르면 같은 상품·순위도 함께 존재할 수 있다.")
        void allowsSameProductAndRankAcrossDifferentPeriods() {
            // arrange
            insertMvRow("mv_product_rank_weekly", "2026-W30", 1, 100L);

            // act & assert
            assertThatCode(() -> insertMvRow("mv_product_rank_weekly", "2026-W31", 1, 100L))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("월간 MV 테이블은")
    class MonthlyMv {

        @Test
        @DisplayName("같은 기간에 같은 상품이 두 번 들어오면 유니크 제약으로 거부한다.")
        void rejectsDuplicateProductInSamePeriod() {
            // arrange
            insertMvRow("mv_product_rank_monthly", "2026-07", 1, 100L);

            // act & assert
            assertThatThrownBy(() -> insertMvRow("mv_product_rank_monthly", "2026-07", 2, 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 기간에 같은 순위가 두 번 들어오면 유니크 제약으로 거부한다.")
        void rejectsDuplicateRankInSamePeriod() {
            // arrange
            insertMvRow("mv_product_rank_monthly", "2026-07", 1, 100L);

            // act & assert
            assertThatThrownBy(() -> insertMvRow("mv_product_rank_monthly", "2026-07", 1, 200L))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("스테이징 테이블은")
    class Staging {

        @Test
        @DisplayName("같은 기간 타입·기간·상품이 두 번 들어오면 유니크 제약으로 거부한다.")
        void rejectsDuplicateProductInSamePeriod() {
            // arrange
            insertStagingRow("WEEKLY", "2026-W30", 100L);

            // act & assert
            assertThatThrownBy(() -> insertStagingRow("WEEKLY", "2026-W30", 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("기간 타입이 다르면 같은 기간·상품도 함께 존재할 수 있다.")
        void allowsSameProductAcrossDifferentPeriodTypes() {
            // arrange
            insertStagingRow("WEEKLY", "2026-W30", 100L);

            // act & assert
            assertThatCode(() -> insertStagingRow("MONTHLY", "2026-W30", 100L))
                .doesNotThrowAnyException();
        }
    }
}
