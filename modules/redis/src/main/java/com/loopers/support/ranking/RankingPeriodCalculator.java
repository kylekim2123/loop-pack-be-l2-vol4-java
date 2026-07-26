package com.loopers.support.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RankingPeriodCalculator {

    private static final String WEEKLY_KEY_FORMAT = "%d-W%02d";
    private static final DateTimeFormatter MONTHLY_KEY_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final int DAYS_TO_WEEK_END = 6;

    public static RankingPeriod weekly(LocalDate date) {
        int weekBasedYear = date.get(WeekFields.ISO.weekBasedYear());
        int weekOfYear = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        String periodKey = String.format(WEEKLY_KEY_FORMAT, weekBasedYear, weekOfYear);

        LocalDate weekStart = date.with(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue());
        LocalDate weekEnd = weekStart.plusDays(DAYS_TO_WEEK_END);

        return new RankingPeriod(periodKey, weekStart, weekEnd);
    }

    public static RankingPeriod monthly(LocalDate date) {
        String periodKey = MONTHLY_KEY_FORMATTER.format(date);

        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = date.with(TemporalAdjusters.lastDayOfMonth());

        return new RankingPeriod(periodKey, monthStart, monthEnd);
    }
}
