package com.loopers.application.activity;

import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.then;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.like.LikeDeletedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserActivityEventHandlerIntegrationTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private UserActivityEventHandler userActivityEventHandler;

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

    @DisplayName("상품 상세를 조회하면 조회 행동이 비동기로 로깅된다.")
    @Test
    void logsProductViewed_whenProductDetailIsRead() {
        // arrange
        ProductModel product = saveProduct();

        // act
        productFacade.readProduct(product.getId());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(userActivityEventHandler).should().logProductViewed(ProductViewedEvent.from(product.getId())));
    }

    @DisplayName("좋아요를 등록하면 좋아요 행동이 비동기로 로깅된다.")
    @Test
    void logsLikeCreated_whenLikeIsCreated() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();

        // act
        likeFacade.createLike(user.getId(), product.getId());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(userActivityEventHandler).should().logLikeCreated(LikeCreatedEvent.of(user.getId(), product.getId())));
    }

    @DisplayName("좋아요를 취소하면 취소 행동이 비동기로 로깅된다.")
    @Test
    void logsLikeDeleted_whenLikeIsDeleted() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();
        likeFacade.createLike(user.getId(), product.getId());

        // act
        likeFacade.deleteLike(user.getId(), product.getId());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(userActivityEventHandler).should().logLikeDeleted(LikeDeletedEvent.of(user.getId(), product.getId())));
    }

    @DisplayName("주문을 생성하면 주문 행동이 비동기로 로깅된다.")
    @Test
    void logsOrderCreated_whenOrderIsCreated() {
        // arrange
        UserModel user = saveUser();
        ProductModel product = saveProduct();

        // act
        OrderInfo orderInfo = orderFacade.createOrder(
            user.getId(), List.of(new OrderItemCommand(product.getId(), 2)), null, ZonedDateTime.now());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            then(userActivityEventHandler).should()
                .logOrderCreated(OrderCreatedEvent.of(orderInfo.orderId(), user.getId(), 78_000)));
    }
}
