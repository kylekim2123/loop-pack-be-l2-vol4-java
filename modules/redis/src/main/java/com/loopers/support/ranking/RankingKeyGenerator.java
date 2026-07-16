package com.loopers.support.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RankingKeyGenerator {

    private static final String RANKING_KEY_FORMAT = "ranking:all:%s";
    private static final DateTimeFormatter DAILY_KEY_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd");

    private RankingKeyGenerator() {
    }

    public static String generate(LocalDate date) {
        return String.format(RANKING_KEY_FORMAT, DAILY_KEY_DATE_FORMATTER.format(date));
    }
}
