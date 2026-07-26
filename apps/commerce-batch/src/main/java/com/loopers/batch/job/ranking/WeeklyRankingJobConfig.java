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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEANUP_NAME = "weeklyRankingStagingCleanup";
    private static final String STEP_AGGREGATE_NAME = "weeklyRankingAggregate";
    private static final String STEP_PUBLISH_NAME = "weeklyRankingPublish";

    private static final String MV_TABLE = "mv_product_rank_weekly";
    private static final RankingPeriodType PERIOD_TYPE = RankingPeriodType.WEEKLY;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final RankingJobComponents rankingJobComponents;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(rankingJobComponents.taskletStep(STEP_CLEANUP_NAME, weeklyRankingStagingCleanupTasklet(null)))
            .next(rankingJobComponents.aggregateStep(
                STEP_AGGREGATE_NAME, weeklyRankingAggregateReader(null), weeklyRankingStagingWriter(null)))
            .next(rankingJobComponents.taskletStep(STEP_PUBLISH_NAME, weeklyRankingPublishTasklet(null)))
            .listener(jobListener)
            .build();
    }

    @StepScope
    @Bean
    public Tasklet weeklyRankingStagingCleanupTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return rankingJobComponents.cleanupTasklet(PERIOD_TYPE, weeklyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductPeriodSum> weeklyRankingAggregateReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return rankingJobComponents.aggregateReader(weeklyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ScoredProductRank> weeklyRankingStagingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return rankingJobComponents.stagingWriter(PERIOD_TYPE, weeklyPeriod(targetDate));
    }

    @StepScope
    @Bean
    public Tasklet weeklyRankingPublishTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return rankingJobComponents.publishTasklet(PERIOD_TYPE, weeklyPeriod(targetDate), MV_TABLE);
    }

    private RankingPeriod weeklyPeriod(String targetDate) {
        return RankingPeriodCalculator.weekly(LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE));
    }
}
