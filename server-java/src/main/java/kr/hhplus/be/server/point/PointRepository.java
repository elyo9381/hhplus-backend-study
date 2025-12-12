package kr.hhplus.be.server.point;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PointRepository extends JpaRepository<PointEntity, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointEntity> findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(UUID userId, LocalDateTime now);
}
