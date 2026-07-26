package com.loopers.interfaces.api.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingResult;
import com.loopers.domain.ranking.RankingPeriodType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter RANKING_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingFacade rankingFacade;

    @Override
    @GetMapping
    public ApiResponse<RankingV1Dto.PageResponse> readRankings(
        @RequestParam(required = false) String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        RankingPeriodType rankingPeriod = parseRankingPeriod(period);
        LocalDate rankingDate = parseRankingDate(date);
        RankingResult result = rankingFacade.readRankings(rankingPeriod, rankingDate, page, size);

        return ApiResponse.success(RankingV1Dto.PageResponse.from(result));
    }

    private RankingPeriodType parseRankingPeriod(String period) {
        if (period == null) {
            return RankingPeriodType.DAILY;
        }

        try {
            return RankingPeriodType.valueOf(period.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다.");
        }
    }

    private LocalDate parseRankingDate(String date) {
        if (date == null) {
            return LocalDate.now(RANKING_ZONE);
        }

        try {
            return LocalDate.parse(date, RANKING_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 uuuuMMdd 형식이어야 합니다.");
        }
    }
}
