package com.loopers.batch.job.ranking;

import javax.sql.DataSource;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.support.ranking.RankingPeriod;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RankingJobComponents {

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

    private static final String MV_PUBLISH_TEMPLATE = """
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
        """;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final RankingScoreProcessor rankingScoreProcessor;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    public Step taskletStep(String stepName, Tasklet tasklet) {
        return new StepBuilder(stepName, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    public Step aggregateStep(
        String stepName,
        JdbcCursorItemReader<ProductPeriodSum> reader,
        JdbcBatchItemWriter<ScoredProductRank> writer
    ) {
        return new StepBuilder(stepName, jobRepository)
            .<ProductPeriodSum, ScoredProductRank>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(rankingScoreProcessor)
            .writer(writer)
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    public JdbcCursorItemReader<ProductPeriodSum> aggregateReader(RankingPeriod period) {
        return new JdbcCursorItemReaderBuilder<ProductPeriodSum>()
            .name("rankingAggregateReader")
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

    public JdbcBatchItemWriter<ScoredProductRank> stagingWriter(RankingPeriodType periodType, RankingPeriod period) {
        return new JdbcBatchItemWriterBuilder<ScoredProductRank>()
            .dataSource(dataSource)
            .sql(STAGING_INSERT_SQL)
            .itemPreparedStatementSetter((item, ps) -> {
                ps.setString(1, periodType.name());
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

    public Tasklet cleanupTasklet(RankingPeriodType periodType, RankingPeriod period) {
        return (contribution, chunkContext) -> {
            jdbcTemplate.update(STAGING_DELETE_SQL, periodType.name(), period.periodKey());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet publishTasklet(RankingPeriodType periodType, RankingPeriod period, String mvTable) {
        String deleteSql = "DELETE FROM " + mvTable + " WHERE period_key = ?";
        String publishSql = String.format(MV_PUBLISH_TEMPLATE, mvTable, TOP_RANK_LIMIT);
        return (contribution, chunkContext) -> {
            jdbcTemplate.update(deleteSql, period.periodKey());
            jdbcTemplate.update(
                publishSql,
                period.periodKey(), period.startDate().toString(), period.endDate().toString(),
                periodType.name(), period.periodKey()
            );
            return RepeatStatus.FINISHED;
        };
    }
}
