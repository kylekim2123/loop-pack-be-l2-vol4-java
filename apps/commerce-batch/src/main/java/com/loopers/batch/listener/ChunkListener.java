package com.loopers.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ChunkListener {

    @AfterChunk
    void afterChunk(ChunkContext chunkContext) {
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        log.info("청크 종료: readCount: {}, writeCount: {}", stepExecution.getReadCount(), stepExecution.getWriteCount());
    }
}
