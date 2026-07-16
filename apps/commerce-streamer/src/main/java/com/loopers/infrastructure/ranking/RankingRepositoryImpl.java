package com.loopers.infrastructure.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScoreEvent;
import com.loopers.support.ranking.RankingKeyGenerator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private static final int RANKING_RETENTION_DAYS = 2;
    private static final long HANDLED_KEY_TTL_SECONDS = Duration.ofDays(RANKING_RETENTION_DAYS).toSeconds();
    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");
    private static final String RANKING_SCORE_APPLY_SCRIPT_PATH = "lua/ranking-score-apply.lua";
    private static final RedisScript<Long> RANKING_SCORE_APPLY_SCRIPT = loadRankingScoreApplyScript();

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final ObjectMapper objectMapper;

    public RankingRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void applyScores(LocalDate rankingDate, List<RankingScoreEvent> events) {
        String rankingKey = RankingKeyGenerator.generate(rankingDate);
        long expireAtEpochSecond = rankingDate.plusDays(RANKING_RETENTION_DAYS)
            .atStartOfDay(RANKING_ZONE)
            .toEpochSecond();

        List<String> scriptArguments = new ArrayList<>();
        scriptArguments.add(String.valueOf(expireAtEpochSecond));
        scriptArguments.add(String.valueOf(HANDLED_KEY_TTL_SECONDS));
        events.forEach(event -> scriptArguments.add(serialize(event)));

        Long appliedEventCount = masterRedisTemplate.execute(
            RANKING_SCORE_APPLY_SCRIPT,
            List.of(rankingKey),
            scriptArguments.toArray(new String[0])
        );

        log.debug("랭킹 점수 적재 완료 - rankingKey={}, appliedEventCount={}", rankingKey, appliedEventCount);
    }

    private String serialize(RankingScoreEvent event) {
        try {
            ObjectNode eventNode = objectMapper.createObjectNode();
            eventNode.put("eventId", event.eventId());

            ArrayNode productScoresNode = eventNode.putArray("productScores");
            for (RankingScoreEvent.ProductScore productScore : event.productScores()) {
                ObjectNode productScoreNode = productScoresNode.addObject();
                productScoreNode.put("productId", String.valueOf(productScore.productId()));
                productScoreNode.put("score", productScore.score());
            }

            return objectMapper.writeValueAsString(eventNode);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(String.format("랭킹 이벤트 직렬화에 실패했습니다. eventId=%s", event.eventId()), e);
        }
    }

    private static RedisScript<Long> loadRankingScoreApplyScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(RANKING_SCORE_APPLY_SCRIPT_PATH));
        script.setResultType(Long.class);
        return script;
    }
}
