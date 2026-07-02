package com.loopers.application.payment;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentFailedEvent;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.config.AsyncConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentCompensationEventHandler {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserCouponRepository userCouponRepository;

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreStock(PaymentFailedEvent event) {
        List<OrderItemModel> orderItems = orderRepository.findActiveItemsByOrderId(event.orderId());

        for (OrderItemModel orderItem : orderItems) {
            ProductModel product = productRepository.getActiveByIdForUpdate(orderItem.getProductId());
            product.increaseStock(orderItem.getQuantity().value());
        }
    }

    @Async(AsyncConfig.EVENT_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreCoupon(PaymentFailedEvent event) {
        OrderModel order = orderRepository.getActiveById(event.orderId());
        if (order.getUserCouponId() == null) {
            return;
        }

        UserCouponModel userCoupon = userCouponRepository.getActiveByIdAndUserId(order.getUserCouponId(), order.getUserId());
        userCoupon.restore();
    }
}
