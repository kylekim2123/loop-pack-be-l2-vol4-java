package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class LikeEventHandlerIntegrationTest {

    private static final String EVENT_THREAD_NAME_PREFIX = "event-";

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword("Kyle!2030")
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private ProductModel saveProduct() {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(1L)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(39_000)
            .rawStock(50)
            .build());
    }

    private int likeCountOf(Long productId) {
        return productJpaRepository.findById(productId).orElseThrow().getLikeCount();
    }

    @DisplayName("좋아요가 커밋된 뒤, 집계가 전용 이벤트 스레드 풀에서 비동기로 처리된다.")
    @Test
    void appliesLikeCountChange_onDedicatedEventThread_afterCommit() {
        // arrange
        UserModel user = saveUser("testuser1");
        ProductModel product = saveProduct();
        AtomicReference<String> aggregationThreadName = new AtomicReference<>();
        willAnswer(invocation -> {
            aggregationThreadName.set(Thread.currentThread().getName());
            return invocation.callRealMethod();
        }).given(productRepository).incrementLikeCount(anyLong());

        // act
        likeFacade.createLike(user.getId(), product.getId());

        // assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertAll(
            () -> assertThat(aggregationThreadName.get()).startsWith(EVENT_THREAD_NAME_PREFIX),
            () -> assertThat(likeCountOf(product.getId())).isEqualTo(1)
        ));
    }

    @DisplayName("집계 처리가 실패해도 좋아요는 이미 커밋되어 남는다.")
    @Test
    void keepsLikeCommitted_whenAggregationFails() {
        // arrange
        UserModel user = saveUser("testuser1");
        ProductModel product = saveProduct();
        willThrow(new RuntimeException("집계 처리 실패"))
            .given(productRepository).incrementLikeCount(anyLong());

        // act
        likeFacade.createLike(user.getId(), product.getId());

        // assert
        assertThat(likeJpaRepository.count()).isEqualTo(1L);
        await().during(Duration.ofMillis(500))
            .untilAsserted(() -> assertThat(likeCountOf(product.getId())).isEqualTo(0));
    }
}
