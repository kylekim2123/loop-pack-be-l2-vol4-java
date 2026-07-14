package com.loopers.interfaces.api.queue;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.application.queue.QueueFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String CONSUMED_USER_ID_ATTRIBUTE = "entryToken.consumedUserId";
    private static final String CONSUMED_TOKEN_ATTRIBUTE = "entryToken.consumedToken";
    private static final String CONSUMED_TTL_ATTRIBUTE = "entryToken.consumedTtl";

    private final QueueFacade queueFacade;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Long userId = resolveUserId(request);
        if (userId == null) {
            return true;
        }

        String token = request.getHeader(ENTRY_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장권이 필요합니다.");
        }

        Optional<Duration> remainingTtl = queueFacade.consumeEntryToken(userId, token);
        if (remainingTtl.isEmpty()) {
            throw new CoreException(ErrorType.FORBIDDEN, "유효하지 않은 입장권입니다.");
        }

        request.setAttribute(CONSUMED_USER_ID_ATTRIBUTE, userId);
        request.setAttribute(CONSUMED_TOKEN_ATTRIBUTE, token);
        request.setAttribute(CONSUMED_TTL_ATTRIBUTE, remainingTtl.get());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object consumedUserId = request.getAttribute(CONSUMED_USER_ID_ATTRIBUTE);
        if (consumedUserId == null) {
            return;
        }

        if (response.getStatus() < HttpStatus.BAD_REQUEST.value()) {
            return;
        }

        Duration remainingTtl = (Duration) request.getAttribute(CONSUMED_TTL_ATTRIBUTE);
        if (remainingTtl.getSeconds() <= 0) {
            return;
        }

        String token = (String) request.getAttribute(CONSUMED_TOKEN_ATTRIBUTE);
        queueFacade.restoreEntryToken((Long) consumedUserId, token, remainingTtl);
    }

    private Long resolveUserId(HttpServletRequest request) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        if (loginId == null) {
            return null;
        }

        return userRepository.findActiveByLoginId(loginId)
            .map(UserModel::getId)
            .orElse(null);
    }
}
