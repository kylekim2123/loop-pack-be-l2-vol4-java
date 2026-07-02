package com.loopers.application.payment;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentFailedEvent;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentRequestResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.domain.payment.PaymentTransactionStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentTransactionWriter {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirm(PaymentModel payment, PaymentTransactionStatus resolved) {
        recordTransactionKeyIfAbsent(payment.getId(), resolved.transactionKey());

        int affectedRows = paymentRepository.confirmIfUnresolved(payment.getId(), resolved.status(), resolved.reason());
        if (affectedRows == 0) {
            cancelIfSucceededAfterFailure(payment.getId(), resolved);
            return false;
        }

        OrderModel order = orderRepository.getActiveById(payment.getOrderId());
        if (resolved.status() == PaymentStatus.SUCCESS) {
            order.markPaid();
            eventPublisher.publishEvent(PaymentSucceededEvent.of(payment.getId(), payment.getOrderId(), payment.getAmount()));
        } else if (resolved.status() == PaymentStatus.FAILED) {
            order.markPaymentFailed();
            eventPublisher.publishEvent(PaymentFailedEvent.of(payment.getId(), payment.getOrderId()));
        }

        return true;
    }

    private void cancelIfSucceededAfterFailure(Long paymentId, PaymentTransactionStatus resolved) {
        if (resolved.status() != PaymentStatus.SUCCESS) {
            return;
        }

        PaymentModel resolvedPayment = paymentRepository.getById(paymentId);
        if (resolvedPayment.isFailed()) {
            paymentGateway.cancel(resolvedPayment);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reapplyRequest(PaymentModel payment, PaymentRequestResult requestResult) {
        PaymentModel reloaded = paymentRepository.getById(payment.getId());
        reloaded.applyRequestResult(requestResult);
        paymentRepository.save(reloaded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void isolate(PaymentModel payment) {
        PaymentModel reloaded = paymentRepository.getById(payment.getId());
        reloaded.markStuck();
        paymentRepository.save(reloaded);
    }

    private void recordTransactionKeyIfAbsent(Long paymentId, String discoveredTransactionKey) {
        if (discoveredTransactionKey == null) {
            return;
        }

        PaymentModel payment = paymentRepository.getById(paymentId);
        if (payment.getTransactionKey() == null) {
            payment.recordTransactionKey(discoveredTransactionKey);
            paymentRepository.save(payment);
        }
    }
}
