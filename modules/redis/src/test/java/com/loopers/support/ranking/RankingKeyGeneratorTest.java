package com.loopers.support.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingKeyGenerator")
class RankingKeyGeneratorTest {

    @Test
    @DisplayName("일반 날짜를 받으면 ranking:all:{uuuuMMdd} 형식의 키를 만든다.")
    void generatesDailyKey_whenDateIsGiven() {
        // arrange
        LocalDate date = LocalDate.of(2026, 7, 17);

        // act
        String key = RankingKeyGenerator.generate(date);

        // assert
        assertThat(key).isEqualTo("ranking:all:20260717");
    }

    @ParameterizedTest
    @MethodSource("yearBoundaryDates")
    @DisplayName("ISO 주차 기준으로는 연도가 어긋나는 연말·연초 경계 날짜도 실제 연도로 키를 만든다.")
    void generatesDailyKey_atYearBoundary(LocalDate date, String expectedKey) {
        // arrange & act
        String key = RankingKeyGenerator.generate(date);

        // assert
        assertThat(key).isEqualTo(expectedKey);
    }

    private static Stream<Arguments> yearBoundaryDates() {
        return Stream.of(
            Arguments.of(LocalDate.of(2024, 12, 28), "ranking:all:20241228"),
            Arguments.of(LocalDate.of(2024, 12, 29), "ranking:all:20241229"),
            Arguments.of(LocalDate.of(2024, 12, 30), "ranking:all:20241230"),
            Arguments.of(LocalDate.of(2024, 12, 31), "ranking:all:20241231"),
            Arguments.of(LocalDate.of(2025, 1, 1), "ranking:all:20250101"),
            Arguments.of(LocalDate.of(2025, 1, 2), "ranking:all:20250102"),
            Arguments.of(LocalDate.of(2025, 1, 3), "ranking:all:20250103")
        );
    }
}
