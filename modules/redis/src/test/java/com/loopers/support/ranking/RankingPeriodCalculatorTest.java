package com.loopers.support.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("RankingPeriodCalculator")
class RankingPeriodCalculatorTest {

    @Nested
    @DisplayName("주간 기간 계산은")
    class Weekly {

        @Test
        @DisplayName("날짜가 속한 ISO 주차 키와 그 주의 월요일~일요일을 돌려준다.")
        void returnsIsoWeekKeyAndMondayToSunday() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 22);

            // act
            RankingPeriod period = RankingPeriodCalculator.weekly(date);

            // assert
            assertAll(
                () -> assertThat(period.periodKey()).isEqualTo("2026-W30"),
                () -> assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 7, 20)),
                () -> assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 7, 26))
            );
        }

        @ParameterizedTest
        @MethodSource("sameWeekDates")
        @DisplayName("같은 주에 속한 어떤 날짜를 넣어도 동일한 기간을 돌려준다.")
        void returnsSamePeriod_forAnyDateInSameWeek(LocalDate date) {
            // act
            RankingPeriod period = RankingPeriodCalculator.weekly(date);

            // assert
            assertAll(
                () -> assertThat(period.periodKey()).isEqualTo("2026-W30"),
                () -> assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 7, 20)),
                () -> assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 7, 26))
            );
        }

        @ParameterizedTest
        @MethodSource("yearBoundaryDates")
        @DisplayName("주 기준 연도가 달력 연도와 어긋나는 연말·연초 날짜도 ISO 주 기준 연도로 키를 만든다.")
        void usesWeekBasedYear_atYearBoundary(LocalDate date, String expectedPeriodKey) {
            // act
            RankingPeriod period = RankingPeriodCalculator.weekly(date);

            // assert
            assertThat(period.periodKey()).isEqualTo(expectedPeriodKey);
        }

        private static Stream<LocalDate> sameWeekDates() {
            return Stream.of(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 26)
            );
        }

        private static Stream<Arguments> yearBoundaryDates() {
            return Stream.of(
                Arguments.of(LocalDate.of(2025, 12, 28), "2025-W52"),
                Arguments.of(LocalDate.of(2025, 12, 29), "2026-W01"),
                Arguments.of(LocalDate.of(2025, 12, 31), "2026-W01"),
                Arguments.of(LocalDate.of(2026, 1, 4), "2026-W01"),
                Arguments.of(LocalDate.of(2026, 12, 28), "2026-W53"),
                Arguments.of(LocalDate.of(2027, 1, 1), "2026-W53"),
                Arguments.of(LocalDate.of(2027, 1, 3), "2026-W53")
            );
        }
    }

    @Nested
    @DisplayName("월간 기간 계산은")
    class Monthly {

        @Test
        @DisplayName("날짜가 속한 캘린더 월 키와 그 달의 1일~말일을 돌려준다.")
        void returnsCalendarMonthKeyAndFirstToLastDay() {
            // arrange
            LocalDate date = LocalDate.of(2026, 7, 22);

            // act
            RankingPeriod period = RankingPeriodCalculator.monthly(date);

            // assert
            assertAll(
                () -> assertThat(period.periodKey()).isEqualTo("2026-07"),
                () -> assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 7, 1)),
                () -> assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 7, 31))
            );
        }

        @ParameterizedTest
        @MethodSource("monthEndDates")
        @DisplayName("평년·윤년에 따라 달라지는 2월 말일도 실제 말일로 끝 날짜를 잡는다.")
        void resolvesLastDayOfFebruary(LocalDate date, LocalDate expectedEnd) {
            // act
            RankingPeriod period = RankingPeriodCalculator.monthly(date);

            // assert
            assertThat(period.endDate()).isEqualTo(expectedEnd);
        }

        private static Stream<Arguments> monthEndDates() {
            return Stream.of(
                Arguments.of(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 28)),
                Arguments.of(LocalDate.of(2028, 2, 10), LocalDate.of(2028, 2, 29))
            );
        }
    }
}
