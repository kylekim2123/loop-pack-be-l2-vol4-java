package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

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
                () -> then(queueRepository).should(never()).findRank(anyLong())
            );
        }

        @DisplayName("아직 대기 중이면 순번·전체 대기 인원과 함께 예상 대기 시간(순번 ÷ 초당 발급 인원, 올림)을 반환한다.")
        @Test
        void returnsWaitingInfoWithEstimatedWaitSeconds_whenStillWaiting() {
            // arrange
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.empty());
            given(queueRepository.findRank(userId)).willReturn(Optional.of(511L));
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
            given(queueRepository.findRank(userId)).willReturn(Optional.of(0L));
            given(queueRepository.count()).willReturn(1L);

            // act
            QueuePositionInfo positionInfo = queueFacade.readPosition(userId);

            // assert
            assertThat(positionInfo.estimatedWaitSeconds()).isEqualTo(1L);
        }

        @DisplayName("입장권 확인과 순번 조회 사이에 스케줄러가 발급을 끝내 줄에서 빠졌어도, 재확인한 입장권으로 응답한다.")
        @Test
        void returnsIssuedInfo_whenTokenIssuedBetweenTokenCheckAndRankRead() {
            // arrange (첫 확인 땐 입장권이 없다가, 순번 조회 실패 후 재확인 땐 발급돼 있음)
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.empty(), Optional.of("entry-token-value"));
            given(queueRepository.findRank(userId)).willReturn(Optional.empty());

            // act
            QueuePositionInfo positionInfo = queueFacade.readPosition(userId);

            // assert
            assertAll(
                () -> assertThat(positionInfo.position()).isZero(),
                () -> assertThat(positionInfo.entryToken()).isEqualTo("entry-token-value")
            );
        }

        @DisplayName("대기열에도 없고 재확인한 입장권도 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNeitherWaitingNorIssued() {
            // arrange
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.empty());
            given(queueRepository.findRank(userId)).willReturn(Optional.empty());

            // act
            CoreException exception = catchThrowableOfType(CoreException.class, () -> queueFacade.readPosition(userId));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    class Enter {

        private final Long userId = 1L;

        @DisplayName("줄에 세운 직후 스케줄러가 발급을 끝내 줄에서 빠졌어도, 입장권으로 응답한다.")
        @Test
        void returnsIssuedInfo_whenSchedulerIssuesRightAfterEnter() {
            // arrange
            given(queueRepository.findRank(userId)).willReturn(Optional.empty());
            given(queueRepository.findEntryToken(userId)).willReturn(Optional.of("entry-token-value"));

            // act
            QueuePositionInfo positionInfo = queueFacade.enter(userId);

            // assert
            assertAll(
                () -> then(queueRepository).should().add(userId),
                () -> assertThat(positionInfo.position()).isZero(),
                () -> assertThat(positionInfo.entryToken()).isEqualTo("entry-token-value")
            );
        }
    }
}
