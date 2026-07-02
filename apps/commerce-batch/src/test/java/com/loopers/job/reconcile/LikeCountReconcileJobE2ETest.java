package com.loopers.job.reconcile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.loopers.batch.job.reconcile.LikeCountReconcileJobConfig;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + LikeCountReconcileJobConfig.JOB_NAME)
class LikeCountReconcileJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(LikeCountReconcileJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                like_count BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS likes (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                product_id BIGINT NOT NULL,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_metrics (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL UNIQUE,
                like_count BIGINT NOT NULL DEFAULT 0,
                sales_count BIGINT NOT NULL DEFAULT 0,
                view_count BIGINT NOT NULL DEFAULT 0,
                last_event_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                deleted_at DATETIME(6) NULL
            )
            """);
        jdbcTemplate.update("DELETE FROM likes");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @DisplayName("이벤트 유실로 어긋난 like_count가 잡 실행 후 likes COUNT 원천과 일치하게 복구된다.")
    @Test
    void reconcilesDriftedLikeCounts_toSourceOfTruth() throws Exception {
        // arrange (원천은 2건인데 파생 카운터들이 유실·중복으로 어긋난 상태)
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("INSERT INTO products (id, like_count) VALUES (1, 10)");
        jdbcTemplate.update("INSERT INTO products (id, like_count) VALUES (2, 5)");
        jdbcTemplate.update("INSERT INTO likes (user_id, product_id) VALUES (1, 1), (2, 1)");
        jdbcTemplate.update("INSERT INTO product_metrics (product_id, like_count) VALUES (1, 99)");

        // act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // assert
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM products WHERE id = 1", Long.class)).isEqualTo(2L),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM products WHERE id = 2", Long.class)).isEqualTo(0L),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM product_metrics WHERE product_id = 1", Long.class)).isEqualTo(2L)
        );
    }

    @DisplayName("이미 원천과 일치하면 잡 실행 후에도 값이 그대로 유지된다.")
    @Test
    void keepsCounts_whenAlreadyConverged() throws Exception {
        // arrange
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("INSERT INTO products (id, like_count) VALUES (1, 1)");
        jdbcTemplate.update("INSERT INTO likes (user_id, product_id) VALUES (1, 1)");

        // act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // assert
        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(jdbcTemplate.queryForObject("SELECT like_count FROM products WHERE id = 1", Long.class)).isEqualTo(1L)
        );
    }
}
