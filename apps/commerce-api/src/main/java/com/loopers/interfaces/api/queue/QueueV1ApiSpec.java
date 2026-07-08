package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "Loopers 주문 대기열 도메인 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "본인 인증된 회원을 대기열에 세우고 현재 순번과 전체 대기 인원을 반환한다. 이미 대기 중이면 순번이 갱신되어 맨 뒤로 밀린다."
    )
    ApiResponse<QueueV1Dto.EnterResponse> enter(@Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser);

    @Operation(
        summary = "순번 조회",
        description = "대기 중인 회원의 현재 순번과 전체 대기 인원을 반환한다. 대기열에 없으면 404를 반환한다."
    )
    ApiResponse<QueueV1Dto.PositionResponse> readPosition(@Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser);
}
