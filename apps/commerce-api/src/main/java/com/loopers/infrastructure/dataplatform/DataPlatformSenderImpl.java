package com.loopers.infrastructure.dataplatform;

import org.springframework.stereotype.Component;

import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.PaymentSucceededEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DataPlatformSenderImpl implements DataPlatformSender {

    @Override
    public void send(OrderCreatedEvent event) {
        log.info("[DataPlatform] 주문 생성 전송 - orderId={}, userId={}, finalAmount={}",
            event.orderId(), event.userId(), event.finalAmount());
    }

    @Override
    public void send(PaymentSucceededEvent event) {
        log.info("[DataPlatform] 결제 완료 전송 - paymentId={}, orderId={}, amount={}",
            event.paymentId(), event.orderId(), event.amount());
    }
}
