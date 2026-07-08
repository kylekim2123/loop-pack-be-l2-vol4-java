package com.loopers.interfaces.api.queue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/queue")
public class QueueV1Controller implements QueueV1ApiSpec {

    private final QueueFacade queueFacade;

    @Override
    @PostMapping("/enter")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QueueV1Dto.EnterResponse> enter(@LoginUser AuthenticatedUser loginUser) {
        QueuePositionInfo queuePositionInfo = queueFacade.enter(loginUser.userId());

        return ApiResponse.success(QueueV1Dto.EnterResponse.from(queuePositionInfo));
    }

    @Override
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> readPosition(@LoginUser AuthenticatedUser loginUser) {
        QueuePositionInfo queuePositionInfo = queueFacade.readPosition(loginUser.userId());

        return ApiResponse.success(QueueV1Dto.PositionResponse.from(queuePositionInfo));
    }
}
