package com.loopers.domain.dataplatform;

import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.PaymentSucceededEvent;

public interface DataPlatformSender {

    void send(OrderCreatedEvent event);

    void send(PaymentSucceededEvent event);
}
