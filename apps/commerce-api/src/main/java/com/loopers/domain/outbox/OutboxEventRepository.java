package com.loopers.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEventModel save(OutboxEventModel outboxEvent);

    List<OutboxEventModel> findUnpublished(int limit);
}
