package com.loopers.application.activity;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeDeletedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.support.config.AsyncConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UserActivityEventHandler {

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logProductViewed(ProductViewedEvent event) {
        log.info("[UserActivity] 상품 조회 - productId={}", event.productId());
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logLikeCreated(LikeCreatedEvent event) {
        log.info("[UserActivity] 좋아요 등록 - userId={}, productId={}", event.userId(), event.productId());
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logLikeDeleted(LikeDeletedEvent event) {
        log.info("[UserActivity] 좋아요 취소 - userId={}, productId={}", event.userId(), event.productId());
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void logOrderCreated(OrderCreatedEvent event) {
        log.info("[UserActivity] 주문 생성 - userId={}, orderId={}", event.userId(), event.orderId());
    }
}
