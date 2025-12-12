package kr.hhplus.be.server.point;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntity, UUID> {
    
    List<PointHistoryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
