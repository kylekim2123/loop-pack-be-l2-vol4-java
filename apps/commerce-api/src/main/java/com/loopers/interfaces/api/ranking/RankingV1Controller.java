package com.loopers.interfaces.api.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingItemInfo;
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
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate rankingDate = parseRankingDate(date);
        Page<RankingItemInfo> rankings = rankingFacade.readRankings(rankingDate, page, size);

        return ApiResponse.success(RankingV1Dto.PageResponse.from(rankings));
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
