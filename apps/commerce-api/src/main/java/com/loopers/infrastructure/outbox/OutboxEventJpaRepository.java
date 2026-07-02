package com.loopers.infrastructure.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.outbox.OutboxEventModel;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, Long> {

    List<OutboxEventModel> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
