package com.loopers.batch.job.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.loopers.batch.listener.JobListener;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.support.ranking.RankingPeriod;
import com.loopers.support.ranking.RankingPeriodCalculator;

import lombok.RequiredArgsConstructor;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEANUP_NAME = "monthlyRankingStagingCleanup";
    private static final String STEP_AGGREGATE_NAME = "monthlyRankingAggregate";
    private static final String STEP_PUBLISH_NAME = "monthlyRankingPublish";

    private static final String MV_TABLE = "mv_product_rank_monthly";
    private static final RankingPeriodType PERIOD_TYPE = RankingPeriodType.MONTHLY;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final RankingJobComponents rankingJobComponents;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(rankingJobComponents.taskletStep(STEP_CLEANUP_NAME, monthlyRankingStagingCleanupTasklet(null)))
            .next(rankingJobComponents.aggregateStep(
                STEP_AGGREGATE_NAME, monthlyRankingAggregateReader(null), monthlyRankingStagingWriter(null)))
            .next(rankingJobComponents.taskletStep(STEP_PUBLISH_NAME, monthlyRankingPublishTasklet(null)))
            .listener(jobListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet monthlyRankingStagingCleanupTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return rankingJobComponents.cleanupTasklet(PERIOD_TYPE, monthlyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductPeriodSum> monthlyRankingAggregateReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return rankingJobComponents.aggregateReader(monthlyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ScoredProductRank> monthlyRankingStagingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return rankingJobComponents.stagingWriter(PERIOD_TYPE, monthlyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public Tasklet monthlyRankingPublishTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return rankingJobComponents.publishTasklet(PERIOD_TYPE, monthlyPeriod(targetDate), MV_TABLE);
    }

    private RankingPeriod monthlyPeriod(String targetDate) {
        return RankingPeriodCalculator.monthly(LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE));
    }
}
