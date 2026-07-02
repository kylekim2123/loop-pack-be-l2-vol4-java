package com.loopers.batch.job.reconcile;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.loopers.batch.job.reconcile.step.ProductLikeCountReconcileTasklet;
import com.loopers.batch.job.reconcile.step.ProductMetricsLikeCountReconcileTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;

import lombok.RequiredArgsConstructor;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class LikeCountReconcileJobConfig {

    public static final String JOB_NAME = "likeCountReconcileJob";
    private static final String STEP_PRODUCT_LIKE_COUNT_NAME = "reconcileProductLikeCount";
    private static final String STEP_PRODUCT_METRICS_LIKE_COUNT_NAME = "reconcileProductMetricsLikeCount";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductLikeCountReconcileTasklet productLikeCountReconcileTasklet;
    private final ProductMetricsLikeCountReconcileTasklet productMetricsLikeCountReconcileTasklet;

    @Bean(JOB_NAME)
    public Job likeCountReconcileJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(reconcileProductLikeCountStep())
            .next(reconcileProductMetricsLikeCountStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_PRODUCT_LIKE_COUNT_NAME)
    public Step reconcileProductLikeCountStep() {
        return new StepBuilder(STEP_PRODUCT_LIKE_COUNT_NAME, jobRepository)
            .tasklet(productLikeCountReconcileTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_PRODUCT_METRICS_LIKE_COUNT_NAME)
    public Step reconcileProductMetricsLikeCountStep() {
        return new StepBuilder(STEP_PRODUCT_METRICS_LIKE_COUNT_NAME, jobRepository)
            .tasklet(productMetricsLikeCountReconcileTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
