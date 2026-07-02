package com.loopers.application.like;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeDeletedEvent;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.config.AsyncConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final ProductRepository productRepository;

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseLikeCount(LikeCreatedEvent event) {
        productRepository.incrementLikeCount(event.productId());
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseLikeCount(LikeDeletedEvent event) {
        productRepository.decrementLikeCount(event.productId());
    }
}
