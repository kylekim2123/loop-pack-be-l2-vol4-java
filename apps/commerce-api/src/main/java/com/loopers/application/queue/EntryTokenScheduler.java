package com.loopers.application.queue;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenScheduler {

    private final QueueFacade queueFacade;

    @Scheduled(fixedDelayString = "${queue.issue-interval}")
    public void issueEntryTokens() {
        List<Long> userIds = queueFacade.findIssuableUserIds();

        for (Long userId : userIds) {
            try {
                queueFacade.issueEntryToken(userId);
            } catch (Exception e) {
                log.warn("입장권 발급 실패 (userId={})", userId, e);
            }
        }
    }
}
