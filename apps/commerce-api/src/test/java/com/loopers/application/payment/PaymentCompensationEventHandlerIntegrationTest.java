package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class PaymentCompensationEventHandlerIntegrationTest {

    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final String TX_KEY = "TX-0001";
    private static final int INITIAL_STOCK = 50;
    private static final int ORDERED_QUANTITY = 2;

    @Autowired
    private PaymentTransactionWriter paymentTransactionWriter;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel saveProductWithOrderedStock() {
        ProductModel product = ProductModel.builder()
            .brandId(1L)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(INITIAL_STOCK)
            .build();
        product.decreaseStock(ORDERED_QUANTITY);

        return productJpaRepository.save(product);
    }

    private OrderModel saveOrderWithItems(Long productId, Long userCouponId) {
        OrderModel order = OrderModel.builder()
            .userId(1L)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(78_000)
            .discountAmount(0)
            .finalAmount(78_000)
            .userCouponId(userCouponId)
            .build();
        OrderItemModel orderItem = OrderItemModel.builder()
            .productId(productId)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(39_000)
            .rawQuantity(ORDERED_QUANTITY)
            .build();

        return orderRepository.save(order, List.of(orderItem));
    }

    private UserCouponModel saveUsedCoupon() {
        return userCouponJpaRepository.save(UserCouponModel.builder()
            .userId(1L)
            .couponId(1L)
            .name("5천원 할인 쿠폰")
            .discountType(DiscountType.FIXED)
            .discountValue(5_000)
            .minOrderAmount(10_000)
            .expiredAt(ZonedDateTime.now().plusDays(7))
            .usedAt(ZonedDateTime.now())
            .build());
    }

    private PaymentModel savePayment(Long orderId) {
        PaymentModel payment = PaymentModel.builder()
            .orderId(orderId)
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(ZonedDateTime.now())
            .build();
        payment.recordTransactionKey(TX_KEY);

        return paymentJpaRepository.save(payment);
    }

    private int stockOf(Long productId) {
        return productJpaRepository.findById(productId).orElseThrow().getStock().value();
    }

    @DisplayName("결제 확정 실패가 커밋된 뒤, 주문은 PAYMENT_FAILED로 전이되고 재고가 비동기로 복원된다.")
    @Test
    void restoresStock_afterPaymentFailureCommit() {
        // arrange
        ProductModel product = saveProductWithOrderedStock();
        OrderModel order = saveOrderWithItems(product.getId(), null);
        PaymentModel payment = savePayment(order.getId());

        // act
        paymentTransactionWriter.confirm(payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.FAILED, "잔액 부족"));

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertAll(
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_FAILED),
            () -> assertThat(stockOf(product.getId())).isEqualTo(INITIAL_STOCK)
        ));
    }

    @DisplayName("쿠폰을 사용한 주문의 결제가 실패하면, 쿠폰이 다시 사용 가능 상태로 복원된다.")
    @Test
    void restoresCoupon_afterPaymentFailureCommit() {
        // arrange
        ProductModel product = saveProductWithOrderedStock();
        UserCouponModel usedCoupon = saveUsedCoupon();
        OrderModel order = saveOrderWithItems(product.getId(), usedCoupon.getId());
        PaymentModel payment = savePayment(order.getId());

        // act
        paymentTransactionWriter.confirm(payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.FAILED, "잔액 부족"));

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(userCouponJpaRepository.findById(usedCoupon.getId()).orElseThrow().getUsedAt()).isNull());
    }

    @DisplayName("같은 결제 실패를 두 번 확정해도 복원은 정확히 한 번만 일어난다.")
    @Test
    void restoresExactlyOnce_whenConfirmedTwice() {
        // arrange
        ProductModel product = saveProductWithOrderedStock();
        OrderModel order = saveOrderWithItems(product.getId(), null);
        PaymentModel payment = savePayment(order.getId());

        // act
        boolean firstResult = paymentTransactionWriter.confirm(
            payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.FAILED, "잔액 부족"));
        boolean secondResult = paymentTransactionWriter.confirm(
            payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.FAILED, "잔액 부족"));

        // assert
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(stockOf(product.getId())).isEqualTo(INITIAL_STOCK));
        await().during(Duration.ofMillis(500))
            .untilAsserted(() -> assertAll(
                () -> assertThat(firstResult).isTrue(),
                () -> assertThat(secondResult).isFalse(),
                () -> assertThat(stockOf(product.getId())).isEqualTo(INITIAL_STOCK)
            ));
    }

    @DisplayName("이미 실패로 확정된 결제에서 뒤늦게 성공이 발견되면 PG 취소를 요청한다.")
    @Test
    void requestsPgCancel_whenSuccessIsDiscoveredAfterFailure() {
        // arrange
        ProductModel product = saveProductWithOrderedStock();
        OrderModel order = saveOrderWithItems(product.getId(), null);
        PaymentModel payment = savePayment(order.getId());
        paymentTransactionWriter.confirm(payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.FAILED, "잔액 부족"));

        // act
        boolean lateResult = paymentTransactionWriter.confirm(
            payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null));

        // assert
        assertAll(
            () -> assertThat(lateResult).isFalse(),
            () -> then(paymentGateway).should().cancel(any(PaymentModel.class))
        );
    }
}
