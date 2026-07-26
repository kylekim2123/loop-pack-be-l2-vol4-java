package com.loopers.job.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    private static final String TARGET_DATE = "20260722";
    private static final String PERIOD_KEY = "2026-W30";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_metrics_daily (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL,
                metric_date DATE NOT NULL,
                like_count BIGINT NOT NULL DEFAULT 0,
                sales_count BIGINT NOT NULL DEFAULT 0,
                view_count BIGINT NOT NULL DEFAULT 0,
                sales_amount BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        jdbcTemplate.update("DELETE FROM product_rank_staging");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jobLauncherTestUtils.setJob(job);
    }

    private void insertDaily(long productId, String metricDate, long view, long like, long salesCount, long salesAmount) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily (product_id, metric_date, like_count, sales_count, view_count, sales_amount, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
            productId, metricDate, like, salesCount, view, salesAmount
        );
    }

    private JobExecution launchWeeklyJob() throws Exception {
        return launchWeeklyJob(TARGET_DATE);
    }

    private JobExecution launchWeeklyJob(String targetDate) throws Exception {
        JobParameters parameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
            .addString("targetDate", targetDate)
            .toJobParameters();
        return jobLauncherTestUtils.launchJob(parameters);
    }

    private long mvRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_weekly", Long.class);
    }

    private long mvRowCountByPeriod(String periodKey) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?", Long.class, periodKey);
    }

    private boolean mvContains(long productId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id = ?", Long.class, productId);
        return count != null && count > 0;
    }

    private int rankOf(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT `rank` FROM mv_product_rank_weekly WHERE product_id = ?", Integer.class, productId);
    }

    private long mvColumn(long productId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM mv_product_rank_weekly WHERE product_id = ?", Long.class, productId);
    }

    private double mvScore(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_weekly WHERE product_id = ?", Double.class, productId);
    }

    private double expectedScore(long view, long like, long salesAmount) {
        return 0.1 * view + 0.2 * like + 0.7 * Math.log1p(salesAmount);
    }

    @DisplayName("기간 밖 원장은 집계에서 제외되고, 그 주에 기록이 없는 상품은 랭킹에 오르지 않는다.")
    @Test
    void aggregatesOnlyWithinTargetWeek() throws Exception {
        // arrange (100번 상품은 그 주 안팎에 기록, 200번 상품은 그 주 밖에만 기록)
        insertDaily(100L, "2026-07-20", 10, 1, 1, 1000);
        insertDaily(100L, "2026-07-26", 5, 0, 0, 0);
        insertDaily(100L, "2026-07-19", 999, 999, 999, 999999);
        insertDaily(100L, "2026-07-27", 999, 999, 999, 999999);
        insertDaily(200L, "2026-07-19", 50, 5, 5, 5000);

        // act
        JobExecution jobExecution = launchWeeklyJob();

        // assert
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(mvRowCount()).isEqualTo(1L),
            () -> assertThat(mvContains(200L)).isFalse(),
            () -> assertThat(rankOf(100L)).isEqualTo(1),
            () -> assertThat(mvColumn(100L, "view_count")).isEqualTo(15L),
            () -> assertThat(mvColumn(100L, "like_count")).isEqualTo(1L),
            () -> assertThat(mvColumn(100L, "sales_count")).isEqualTo(1L),
            () -> assertThat(mvColumn(100L, "sales_amount")).isEqualTo(1000L),
            () -> assertThat(mvScore(100L)).isCloseTo(expectedScore(15, 1, 1000), within(1e-9))
        );
    }

    @DisplayName("같은 targetDate로 다시 실행해도 MV 결과(순위·점수)가 동일하게 수렴한다.")
    @Test
    void reRunProducesIdenticalResult() throws Exception {
        // arrange
        insertDaily(100L, "2026-07-20", 10, 2, 3, 30000);
        insertDaily(200L, "2026-07-22", 5, 1, 1, 1000);

        // act
        launchWeeklyJob();
        int firstRank100 = rankOf(100L);
        double firstScore100 = mvScore(100L);
        JobExecution secondExecution = launchWeeklyJob();

        // assert
        assertAll(
            () -> assertThat(secondExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(mvRowCount()).isEqualTo(2L),
            () -> assertThat(rankOf(100L)).isEqualTo(firstRank100),
            () -> assertThat(mvScore(100L)).isEqualTo(firstScore100)
        );
    }

    @DisplayName("점수가 같으면 product_id 오름차순으로 순위를 확정한다.")
    @Test
    void breaksTieByProductIdAscending() throws Exception {
        // arrange (5번·10번 상품이 완전히 동일한 지표 → 동점)
        insertDaily(5L, "2026-07-22", 10, 2, 1, 5000);
        insertDaily(10L, "2026-07-22", 10, 2, 1, 5000);

        // act
        launchWeeklyJob();

        // assert
        assertAll(
            () -> assertThat(rankOf(5L)).isEqualTo(1),
            () -> assertThat(rankOf(10L)).isEqualTo(2)
        );
    }

    @DisplayName("상품이 101개면 점수 상위 100개만 MV에 적재되고 최하위 상품은 제외된다.")
    @Test
    void keepsOnlyTopHundred() throws Exception {
        // arrange (판매금액이 클수록 높은 점수 → 1번 상품이 최하위)
        for (long productId = 1L; productId <= 101L; productId++) {
            insertDaily(productId, "2026-07-22", 0, 0, 0, productId * 1000);
        }

        // act
        launchWeeklyJob();

        // assert
        assertAll(
            () -> assertThat(mvRowCount()).isEqualTo(100L),
            () -> assertThat(rankOf(101L)).isEqualTo(1),
            () -> assertThat(mvContains(1L)).isFalse(),
            () -> assertThat(mvContains(2L)).isTrue()
        );
    }

    @DisplayName("targetDate로 다른 주를 집계하면 각 주의 MV가 독립적으로 함께 쌓인다.")
    @Test
    void backfillDifferentWeeksAccumulateIndependently() throws Exception {
        // arrange (같은 상품이 2026-W30·2026-W31에 각각 기록)
        insertDaily(100L, "2026-07-22", 10, 1, 1, 1000);
        insertDaily(100L, "2026-07-29", 20, 2, 2, 2000);

        // act (지난주·이번주를 각각 파라미터로 집계)
        launchWeeklyJob("20260722");
        launchWeeklyJob("20260729");

        // assert
        assertAll(
            () -> assertThat(mvRowCount()).isEqualTo(2L),
            () -> assertThat(mvRowCountByPeriod("2026-W30")).isEqualTo(1L),
            () -> assertThat(mvRowCountByPeriod("2026-W31")).isEqualTo(1L)
        );
    }
}
