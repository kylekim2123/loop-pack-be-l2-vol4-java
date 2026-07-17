package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.CarryOverResult;
import com.loopers.domain.ranking.RankingCarryOverProperties;
import com.loopers.domain.ranking.RankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final RankingCarryOverProperties rankingCarryOverProperties;

    @Scheduled(cron = "${ranking.carry-over.cron}")
    public void carryOver() {
        carryOver(LocalDate.now(SEOUL_ZONE));
    }

    public void carryOver(LocalDate baseDate) {
        CarryOverResult result = rankingRepository.carryOverScores(baseDate, rankingCarryOverProperties.ratio());

        switch (result) {
            case CARRIED_OVER ->
                log.info("랭킹 점수 carry-over 완료 - baseDate={}", baseDate);
            case TARGET_ALREADY_EXISTS ->
                log.info("랭킹 점수 carry-over skip: 다음날 랭킹판이 이미 존재합니다 - baseDate={}", baseDate);
            case SOURCE_MISSING ->
                log.info("랭킹 점수 carry-over skip: 오늘 랭킹판이 존재하지 않습니다 - baseDate={}", baseDate);
        }
    }
}
