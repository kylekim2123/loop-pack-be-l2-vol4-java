package com.loopers.infrastructure.outbox;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEventModel save(OutboxEventModel outboxEvent) {
        return outboxEventJpaRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEventModel> findUnpublished(int limit) {
        return outboxEventJpaRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, limit));
    }
}
