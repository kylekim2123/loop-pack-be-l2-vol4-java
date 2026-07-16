package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "Loopers 실시간 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 날짜(uuuuMMdd, 생략 시 오늘)의 인기 상품 랭킹을 점수 내림차순으로 페이지 조회한다. 각 항목은 순위·상품·브랜드 정보를 포함하며, 삭제된 상품은 응답에서 제외된다."
    )
    ApiResponse<RankingV1Dto.PageResponse> readRankings(String date, int page, int size);
}
