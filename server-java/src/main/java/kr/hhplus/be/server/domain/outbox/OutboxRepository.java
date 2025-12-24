package kr.hhplus.be.server.domain.outbox;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
    Outbox save(Outbox outbox);
    List<Outbox> findByStatus(OutboxStatus status);
    List<Outbox> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetryCount);
    void deleteAll();
}
