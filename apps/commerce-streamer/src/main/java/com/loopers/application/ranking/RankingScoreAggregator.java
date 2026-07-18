package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.application.event.LikeDeletedEvent;
import com.loopers.application.event.OrderCreatedEvent;
import com.loopers.application.event.ProductActivityEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingScoreEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RankingScoreAggregator {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final RankingScoreCalculator rankingScoreCalculator;
    private final RankingRepository rankingRepository;

    public void aggregate(List<ProductActivityEvent> events) {
        Map<LocalDate, List<RankingScoreEvent>> rankingScoreEventsByDate = events.stream()
            .collect(Collectors.groupingBy(this::rankingDateOf, Collectors.mapping(this::toRankingScoreEvent, Collectors.toList())));

        rankingScoreEventsByDate.forEach(rankingRepository::applyScores);
    }

    private LocalDate rankingDateOf(ProductActivityEvent event) {
        return event.occurredAt().withZoneSameInstant(SEOUL_ZONE).toLocalDate();
    }

    private RankingScoreEvent toRankingScoreEvent(ProductActivityEvent event) {
        List<RankingScoreEvent.ProductScore> productScores = switch (event) {
            case ProductViewedEvent viewed ->
                List.of(new RankingScoreEvent.ProductScore(viewed.productId(), rankingScoreCalculator.productViewedScore()));
            case LikeCreatedEvent likeCreated ->
                List.of(new RankingScoreEvent.ProductScore(likeCreated.productId(), rankingScoreCalculator.likeCreatedScore()));
            case LikeDeletedEvent likeDeleted ->
                List.of(new RankingScoreEvent.ProductScore(likeDeleted.productId(), rankingScoreCalculator.likeDeletedScore()));
            case OrderCreatedEvent orderCreated -> orderCreated.items().stream()
                .map(item -> new RankingScoreEvent.ProductScore(item.productId(), rankingScoreCalculator.orderItemScore(item.price(), item.quantity())))
                .toList();
        };
        return new RankingScoreEvent(event.eventId(), productScores);
    }
}
