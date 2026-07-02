package com.loopers.batch.job.reconcile.step;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.loopers.batch.job.reconcile.LikeCountReconcileJobConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountReconcileJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class ProductLikeCountReconcileTasklet implements Tasklet {

    private static final String RECONCILE_SQL = """
        UPDATE products p
        LEFT JOIN (
            SELECT product_id, COUNT(*) AS like_count
            FROM likes
            WHERE deleted_at IS NULL
            GROUP BY product_id
        ) src ON src.product_id = p.id
        SET p.like_count = COALESCE(src.like_count, 0), p.updated_at = NOW(6)
        WHERE p.like_count <> COALESCE(src.like_count, 0)
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int reconciledCount = jdbcTemplate.update(RECONCILE_SQL);
        log.info("products.like_count 원천(likes COUNT) 수렴 보정 완료 - 보정 행 수: {}", reconciledCount);

        return RepeatStatus.FINISHED;
    }
}
