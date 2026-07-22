package com.loopers.batch.job.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.support.ranking.RankingPeriod;
import com.loopers.support.ranking.RankingPeriodCalculator;

import lombok.RequiredArgsConstructor;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEANUP_NAME = "weeklyRankingStagingCleanup";
    private static final String STEP_AGGREGATE_NAME = "weeklyRankingAggregate";
    private static final String STEP_PUBLISH_NAME = "weeklyRankingPublish";

    private static final String MV_TABLE = "mv_product_rank_weekly";
    private static final String PERIOD_TYPE = RankingPeriodType.WEEKLY.name();
    private static final int CHUNK_SIZE = 500;
    private static final int TOP_RANK_LIMIT = 100;

    private static final String AGGREGATE_SQL = """
        SELECT product_id,
               SUM(view_count) AS view_count,
               SUM(like_count) AS like_count,
               SUM(sales_count) AS sales_count,
               SUM(sales_amount) AS sales_amount
        FROM product_metrics_daily
        WHERE metric_date BETWEEN ? AND ?
        GROUP BY product_id
        """;

    private static final String STAGING_INSERT_SQL = """
        INSERT INTO product_rank_staging
            (period_type, period_key, product_id, score, like_count, sales_count, view_count, sales_amount, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(6), NOW(6))
        """;

    private static final String STAGING_DELETE_SQL =
        "DELETE FROM product_rank_staging WHERE period_type = ? AND period_key = ?";

    private static final String MV_DELETE_SQL = "DELETE FROM " + MV_TABLE + " WHERE period_key = ?";

    private static final String MV_PUBLISH_SQL = String.format("""
        INSERT INTO %s
            (period_key, period_start, period_end, `rank`, product_id, score,
             like_count, sales_count, view_count, sales_amount, created_at, updated_at)
        SELECT ?, ?, ?,
               ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC),
               product_id, score, like_count, sales_count, view_count, sales_amount, NOW(6), NOW(6)
        FROM product_rank_staging
        WHERE period_type = ? AND period_key = ?
        ORDER BY score DESC, product_id ASC
        LIMIT %d
        """, MV_TABLE, TOP_RANK_LIMIT);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final RankingScoreProcessor rankingScoreProcessor;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyRankingStagingCleanupStep())
            .next(weeklyRankingAggregateStep())
            .next(weeklyRankingPublishStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEANUP_NAME)
    public Step weeklyRankingStagingCleanupStep() {
        return new StepBuilder(STEP_CLEANUP_NAME, jobRepository)
            .tasklet(weeklyRankingStagingCleanupTasklet(null), transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE_NAME)
    public Step weeklyRankingAggregateStep() {
        return new StepBuilder(STEP_AGGREGATE_NAME, jobRepository)
            .<ProductPeriodSum, ScoredProductRank>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankingAggregateReader(null))
            .processor(rankingScoreProcessor)
            .writer(weeklyRankingStagingWriter(null))
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @JobScope
    @Bean(STEP_PUBLISH_NAME)
    public Step weeklyRankingPublishStep() {
        return new StepBuilder(STEP_PUBLISH_NAME, jobRepository)
            .tasklet(weeklyRankingPublishTasklet(null), transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet weeklyRankingStagingCleanupTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        RankingPeriod period = weeklyPeriod(targetDate);
        return (contribution, chunkContext) -> {
            jdbcTemplate.update(STAGING_DELETE_SQL, PERIOD_TYPE, period.periodKey());
            return RepeatStatus.FINISHED;
        };
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductPeriodSum> weeklyRankingAggregateReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        RankingPeriod period = weeklyPeriod(targetDate);
        return new JdbcCursorItemReaderBuilder<ProductPeriodSum>()
            .name("weeklyRankingAggregateReader")
            .dataSource(dataSource)
            .sql(AGGREGATE_SQL)
            .preparedStatementSetter(ps -> {
                ps.setString(1, period.startDate().toString());
                ps.setString(2, period.endDate().toString());
            })
            .rowMapper((rs, rowNum) -> new ProductPeriodSum(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count"),
                rs.getLong("sales_amount")
            ))
            .build();
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ScoredProductRank> weeklyRankingStagingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        RankingPeriod period = weeklyPeriod(targetDate);
        return new JdbcBatchItemWriterBuilder<ScoredProductRank>()
            .dataSource(dataSource)
            .sql(STAGING_INSERT_SQL)
            .itemPreparedStatementSetter((item, ps) -> {
                ps.setString(1, PERIOD_TYPE);
                ps.setString(2, period.periodKey());
                ps.setLong(3, item.productId());
                ps.setDouble(4, item.score());
                ps.setLong(5, item.likeCount());
                ps.setLong(6, item.salesCount());
                ps.setLong(7, item.viewCount());
                ps.setLong(8, item.salesAmount());
            })
            .build();
    }

    @StepScope
    @Bean
    public Tasklet weeklyRankingPublishTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        RankingPeriod period = weeklyPeriod(targetDate);
        return (contribution, chunkContext) -> {
            jdbcTemplate.update(MV_DELETE_SQL, period.periodKey());
            jdbcTemplate.update(
                MV_PUBLISH_SQL,
                period.periodKey(), period.startDate().toString(), period.endDate().toString(),
                PERIOD_TYPE, period.periodKey()
            );
            return RepeatStatus.FINISHED;
        };
    }

    private RankingPeriod weeklyPeriod(String targetDate) {
        return RankingPeriodCalculator.weekly(LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE));
    }
}
