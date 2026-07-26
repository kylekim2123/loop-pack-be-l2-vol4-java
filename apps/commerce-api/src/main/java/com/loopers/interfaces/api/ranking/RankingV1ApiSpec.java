package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "Loopers 실시간 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 기간(period=DAILY|WEEKLY|MONTHLY, 생략 시 DAILY)과 날짜(uuuuMMdd, 생략 시 오늘)가 속한 기간의 인기 상품 랭킹을 순위 순으로 페이지 조회한다. "
            + "일간은 실시간(Redis), 주간·월간은 집계(MV)에서 조회하며, 응답에 조회된 기간(periodKey·기간 시작·끝)을 명시한다. 삭제된 상품은 응답에서 제외된다."
    )
    ApiResponse<RankingV1Dto.PageResponse> readRankings(String period, String date, int page, int size);
}
