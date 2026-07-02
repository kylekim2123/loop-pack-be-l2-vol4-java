package com.loopers.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventType;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.kafka.KafkaMessagePublisher;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.config.KafkaTopicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

import java.time.ZonedDateTime;

@SpringBootTest
class OutboxIntegrationTest {

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @MockitoSpyBean
    private KafkaMessagePublisher kafkaMessagePublisher;

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

    @DisplayName("좋아요가 커밋되면 같은 트랜잭션 안에서 Outbox 행이 함께 기록된다.")
    @Test
    void recordsOutboxRow_withinSameTransaction_whenLikeIsCreated() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();

        // act
        likeFacade.createLike(user.getId(), product.getId());

        // assert
        List<OutboxEventModel> outboxEvents = outboxEventJpaRepository.findAll();
        assertAll(
            () -> assertThat(outboxEvents).hasSize(1),
            () -> assertThat(outboxEvents.get(0).getTopic()).isEqualTo(KafkaTopicConfig.CATALOG_EVENTS_TOPIC),
            () -> assertThat(outboxEvents.get(0).getEventType()).isEqualTo(OutboxEventType.LIKE_CREATED.name()),
            () -> assertThat(outboxEvents.get(0).getPartitionKey()).isEqualTo(String.valueOf(product.getId()))
        );
    }

    @DisplayName("비즈니스 트랜잭션이 롤백되면 Outbox 행도 함께 롤백된다.")
    @Test
    void rollsBackOutboxRow_whenBusinessTransactionRollsBack() {
        // act
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(LikeCreatedEvent.of(1L, 1L));
            status.setRollbackOnly();
        });

        // assert
        assertThat(outboxEventJpaRepository.count()).isZero();
    }

    @DisplayName("주문이 커밋되면 주문 항목 스냅샷을 담은 Outbox 행이 기록된다.")
    @Test
    void recordsOrderCreatedOutboxRow_withItemSnapshot() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();

        // act
        orderFacade.createOrder(user.getId(), List.of(new OrderItemCommand(product.getId(), 2)), null, ZonedDateTime.now());

        // assert
        List<OutboxEventModel> outboxEvents = outboxEventJpaRepository.findAll();
        assertAll(
            () -> assertThat(outboxEvents).hasSize(1),
            () -> assertThat(outboxEvents.get(0).getTopic()).isEqualTo(KafkaTopicConfig.ORDER_EVENTS_TOPIC),
            () -> assertThat(outboxEvents.get(0).getEventType()).isEqualTo(OutboxEventType.ORDER_CREATED.name()),
            () -> assertThat(outboxEvents.get(0).getPayload()).contains("\"quantity\":2")
        );
    }

    @DisplayName("쿠폰 발급 요청이 접수되면 PENDING 요청과 Outbox 행이 한 트랜잭션으로 저장된다.")
    @Test
    void recordsIssueRequestAndOutboxRow_atomically_whenCouponIssueIsRequested() {
        // arrange
        UserModel user = saveUser();
        CouponModel coupon = couponJpaRepository.save(CouponModel.builder()
            .rawName("선착순 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .maxQuantity(100)
            .build());

        // act
        CouponIssueRequestInfo issueRequestInfo = couponFacade.createCouponIssueRequest(
            user.getId(), coupon.getId(), ZonedDateTime.now());

        // assert
        List<OutboxEventModel> outboxEvents = outboxEventJpaRepository.findAll();
        assertAll(
            () -> assertThat(issueRequestInfo.status()).isEqualTo(CouponIssueRequestStatus.PENDING),
            () -> assertThat(outboxEvents).hasSize(1),
            () -> assertThat(outboxEvents.get(0).getTopic()).isEqualTo(KafkaTopicConfig.COUPON_ISSUE_REQUESTS_TOPIC),
            () -> assertThat(outboxEvents.get(0).getEventType()).isEqualTo(OutboxEventType.COUPON_ISSUE_REQUESTED.name()),
            () -> assertThat(outboxEvents.get(0).getPartitionKey()).isEqualTo(String.valueOf(coupon.getId()))
        );
    }

    @DisplayName("relay가 미발행 행을 Kafka로 발행하고 성공 확인 후에만 발행 완료로 표시한다.")
    @Test
    void publishesAndMarksPublished_afterSendSucceeds() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();
        likeFacade.createLike(user.getId(), product.getId());

        // act
        outboxRelayScheduler.relay();

        // assert
        assertThat(outboxEventJpaRepository.findAll())
            .isNotEmpty()
            .allSatisfy(outboxEvent -> assertThat(outboxEvent.isPublished()).isTrue());
    }

    @DisplayName("발행이 실패하면 미발행으로 남고 다음 주기에 재전달된다.")
    @Test
    void retriesOnNextCycle_whenPublishFails() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();
        likeFacade.createLike(user.getId(), product.getId());
        willThrow(new CoreException(ErrorType.INTERNAL_ERROR, "Kafka 발행에 실패했습니다."))
            .willCallRealMethod()
            .given(kafkaMessagePublisher).publish(eq(KafkaTopicConfig.CATALOG_EVENTS_TOPIC), anyString(), any());

        // act
        outboxRelayScheduler.relay();
        List<OutboxEventModel> afterFailedCycle = outboxEventJpaRepository.findAll();
        outboxRelayScheduler.relay();
        List<OutboxEventModel> afterRetryCycle = outboxEventJpaRepository.findAll();

        // assert
        assertAll(
            () -> assertThat(afterFailedCycle)
                .isNotEmpty()
                .allSatisfy(outboxEvent -> assertThat(outboxEvent.isPublished()).isFalse()),
            () -> assertThat(afterRetryCycle)
                .isNotEmpty()
                .allSatisfy(outboxEvent -> assertThat(outboxEvent.isPublished()).isTrue())
        );
    }

    @DisplayName("상품 상세 조회는 Outbox를 거치지 않고 Kafka로 직접 발행된다.")
    @Test
    void publishesProductViewedDirectly_withoutOutbox() {
        // arrange
        ProductModel product = saveProduct();

        // act
        productFacade.readProduct(product.getId());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(kafkaMessagePublisher).should()
                .publish(eq(KafkaTopicConfig.CATALOG_EVENTS_TOPIC), eq(String.valueOf(product.getId())), any()));
        assertThat(outboxEventJpaRepository.count()).isZero();
    }
}
