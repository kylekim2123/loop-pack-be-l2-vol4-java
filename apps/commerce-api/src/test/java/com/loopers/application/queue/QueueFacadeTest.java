package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.queue.QueueRepository;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    private static final QueueProperties QUEUE_PROPERTIES = new QueueProperties(
        14, Duration.ofMinutes(5), Duration.ofMillis(100)
    );

    @Mock
    private QueueRepository queueRepository;

    private QueueFacade queueFacade;

    @BeforeEach
    void setUp() {
        queueFacade = new QueueFacade(queueRepository, QUEUE_PROPERTIES);
    }

    @DisplayName("순번을 조회할 때,")
    @Nested
    class ReadPosition {

        private final Long userId = 1L;

        @DisplayName("입장권이 발급돼 있으면 순번 0과 입장권을 반환하고, 대기열 순번은 조회하지 않는다.")
        @Test
        void returnsIssuedInfo_whenEntryTokenExists() {
            // arrange
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.of("entry-token-value"));

            // act
            QueuePositionInfo positionInfo = queueFacade.readPosition(userId);

            // assert
            assertAll(
                () -> assertThat(positionInfo.position()).isZero(),
                () -> assertThat(positionInfo.entryToken()).isEqualTo("entry-token-value"),
                () -> assertThat(positionInfo.totalWaiting()).isNull(),
                () -> assertThat(positionInfo.estimatedWaitSeconds()).isNull(),
                () -> then(queueRepository).should(never()).getRank(anyLong())
            );
        }

        @DisplayName("아직 대기 중이면 순번·전체 대기 인원과 함께 예상 대기 시간(순번 ÷ 초당 발급 인원, 올림)을 반환한다.")
        @Test
        void returnsWaitingInfoWithEstimatedWaitSeconds_whenStillWaiting() {
            // arrange
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.empty());
            given(queueRepository.getRank(userId)).willReturn(511L);
            given(queueRepository.count()).willReturn(1_200L);

            // act
            QueuePositionInfo positionInfo = queueFacade.readPosition(userId);

            // assert
            assertAll(
                () -> assertThat(positionInfo.position()).isEqualTo(512L),
                () -> assertThat(positionInfo.totalWaiting()).isEqualTo(1_200L),
                () -> assertThat(positionInfo.estimatedWaitSeconds()).isEqualTo(4L),
                () -> assertThat(positionInfo.entryToken()).isNull()
            );
        }

        @DisplayName("순번이 초당 발급 인원보다 작아도 예상 대기 시간은 최소 1초로 계산된다.")
        @Test
        void returnsAtLeastOneSecond_whenPositionIsSmallerThanIssuesPerSecond() {
            // arrange
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.empty());
            given(queueRepository.getRank(userId)).willReturn(0L);
            given(queueRepository.count()).willReturn(1L);

            // act
            QueuePositionInfo positionInfo = queueFacade.readPosition(userId);

            // assert
            assertThat(positionInfo.estimatedWaitSeconds()).isEqualTo(1L);
        }
    }
}
