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

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

    private static final String TARGET_DATE = "20260722";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
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
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jobLauncherTestUtils.setJob(job);
    }

    private void insertDaily(long productId, String metricDate, long view, long like, long salesCount, long salesAmount) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily (product_id, metric_date, like_count, sales_count, view_count, sales_amount, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
            productId, metricDate, like, salesCount, view, salesAmount
        );
    }

    private JobExecution launchMonthlyJob() throws Exception {
        JobParameters parameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
            .addString("targetDate", TARGET_DATE)
            .toJobParameters();
        return jobLauncherTestUtils.launchJob(parameters);
    }

    private long mvRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_monthly", Long.class);
    }

    private boolean mvContains(long productId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE product_id = ?", Long.class, productId);
        return count != null && count > 0;
    }

    private int rankOf(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT `rank` FROM mv_product_rank_monthly WHERE product_id = ?", Integer.class, productId);
    }

    private long mvColumn(long productId, String column) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM mv_product_rank_monthly WHERE product_id = ?", Long.class, productId);
    }

    private double mvScore(long productId) {
        return jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_monthly WHERE product_id = ?", Double.class, productId);
    }

    private double expectedScore(long view, long like, long salesAmount) {
        return 0.1 * view + 0.2 * like + 0.7 * Math.log1p(salesAmount);
    }

    @DisplayName("전월 말일·익월 1일 원장은 제외되고, 그 달에 기록이 없는 상품은 랭킹에 오르지 않는다.")
    @Test
    void aggregatesOnlyWithinTargetMonth() throws Exception {
        // arrange (100번은 그 달 안팎에 기록, 200번은 그 달 밖에만 기록)
        insertDaily(100L, "2026-07-01", 10, 1, 1, 1000);
        insertDaily(100L, "2026-07-31", 5, 0, 0, 0);
        insertDaily(100L, "2026-06-30", 999, 999, 999, 999999);
        insertDaily(100L, "2026-08-01", 999, 999, 999, 999999);
        insertDaily(200L, "2026-06-30", 50, 5, 5, 5000);

        // act
        JobExecution jobExecution = launchMonthlyJob();

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
        insertDaily(100L, "2026-07-05", 10, 2, 3, 30000);
        insertDaily(200L, "2026-07-20", 5, 1, 1, 1000);

        // act
        launchMonthlyJob();
        int firstRank100 = rankOf(100L);
        double firstScore100 = mvScore(100L);
        JobExecution secondExecution = launchMonthlyJob();

        // assert
        assertAll(
            () -> assertThat(secondExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(mvRowCount()).isEqualTo(2L),
            () -> assertThat(rankOf(100L)).isEqualTo(firstRank100),
            () -> assertThat(mvScore(100L)).isEqualTo(firstScore100)
        );
    }

    @DisplayName("월간은 서로 다른 주에 걸친 날짜를 한 달로 합산한다(주간 집계와 다른 결과).")
    @Test
    void aggregatesWholeMonthAcrossWeeks() throws Exception {
        // arrange (2026-07-06은 W28, 2026-07-27은 W31 — 서로 다른 주지만 같은 달)
        insertDaily(100L, "2026-07-06", 10, 0, 0, 0);
        insertDaily(100L, "2026-07-27", 20, 0, 0, 0);

        // act
        launchMonthlyJob();

        // assert
        assertAll(
            () -> assertThat(mvRowCount()).isEqualTo(1L),
            () -> assertThat(rankOf(100L)).isEqualTo(1),
            () -> assertThat(mvColumn(100L, "view_count")).isEqualTo(30L)
        );
    }
}
