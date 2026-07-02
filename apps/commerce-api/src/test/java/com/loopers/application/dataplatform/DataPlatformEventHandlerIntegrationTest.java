package com.loopers.application.dataplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.payment.PaymentTransactionWriter;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.dataplatform.DataPlatformSender;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentSucceededEvent;
import com.loopers.domain.payment.PaymentTransactionStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class DataPlatformEventHandlerIntegrationTest {

    private static final String EVENT_THREAD_NAME_PREFIX = "event-";
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final String TX_KEY = "TX-0001";

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private PaymentTransactionWriter paymentTransactionWriter;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private DataPlatformSender dataPlatformSender;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser() {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId("testuser1")
            .rawPassword("Kyle!2030")
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail("testuser1@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private ProductModel saveProduct() {
        BrandModel brand = brandJpaRepository.save(BrandModel.builder()
            .rawName("감성 브랜드")
            .rawDescription("감성을 담은 브랜드")
            .build());

        return productJpaRepository.save(ProductModel.builder()
            .brandId(brand.getId())
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build());
    }

    private PaymentModel savePaymentWithOrder() {
        OrderModel order = orderJpaRepository.save(OrderModel.builder()
            .userId(1L)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(78_000)
            .discountAmount(0)
            .finalAmount(78_000)
            .build());
        PaymentModel payment = PaymentModel.builder()
            .orderId(order.getId())
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(ZonedDateTime.now())
            .build();
        payment.recordTransactionKey(TX_KEY);

        return paymentJpaRepository.save(payment);
    }

    @DisplayName("주문이 커밋된 뒤, 데이터플랫폼 전송이 전용 이벤트 스레드 풀에서 비동기로 수행된다.")
    @Test
    void sendsOrderCreated_onDedicatedEventThread_afterCommit() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();
        AtomicReference<String> sendThreadName = new AtomicReference<>();
        AtomicReference<OrderCreatedEvent> sentEvent = new AtomicReference<>();
        willAnswer(invocation -> {
            sendThreadName.set(Thread.currentThread().getName());
            sentEvent.set(invocation.getArgument(0));
            return null;
        }).given(dataPlatformSender).send(any(OrderCreatedEvent.class));

        // act
        OrderInfo orderInfo = orderFacade.createOrder(
            user.getId(), List.of(new OrderItemCommand(product.getId(), 2)), null, ZonedDateTime.now());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertAll(
            () -> assertThat(sendThreadName.get()).startsWith(EVENT_THREAD_NAME_PREFIX),
            () -> assertThat(sentEvent.get()).isEqualTo(OrderCreatedEvent.of(orderInfo.orderId(), user.getId(), 78_000))
        ));
    }

    @DisplayName("데이터플랫폼 전송이 실패해도 주문은 이미 커밋되어 남는다.")
    @Test
    void keepsOrderCommitted_whenSendFails() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();
        willThrow(new RuntimeException("데이터플랫폼 전송 실패"))
            .given(dataPlatformSender).send(any(OrderCreatedEvent.class));

        // act
        OrderInfo orderInfo = orderFacade.createOrder(
            user.getId(), List.of(new OrderItemCommand(product.getId(), 2)), null, ZonedDateTime.now());

        // assert
        assertThat(orderJpaRepository.findById(orderInfo.orderId())).isPresent();
    }

    @DisplayName("결제 확정이 커밋된 뒤, 결제 완료 전송이 비동기로 수행된다.")
    @Test
    void sendsPaymentSucceeded_afterConfirmCommit() {
        // arrange
        PaymentModel payment = savePaymentWithOrder();

        // act
        paymentTransactionWriter.confirm(payment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null));

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(dataPlatformSender).should()
                .send(PaymentSucceededEvent.of(payment.getId(), payment.getOrderId(), 78_000)));
    }
}
