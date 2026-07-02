package com.loopers.application.dataplatform;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.support.config.AsyncConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataPlatformEventHandler {

    private final DataPlatformSender dataPlatformSender;

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendOrderCreated(OrderCreatedEvent event) {
        dataPlatformSender.send(event);
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPaymentSucceeded(PaymentSucceededEvent event) {
        dataPlatformSender.send(event);
    }
}
