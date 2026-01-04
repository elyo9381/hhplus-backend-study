package kr.hhplus.be.server.infrastructure.point.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Point JPA Repository
 * 
 * 동시성 제어: 비관적 락 (PESSIMISTIC_WRITE)
 * - 포인트 사용 시 잔액 음수 방지
 * - SELECT ... FOR UPDATE로 행 락 획득
 */
public interface PointRepository extends JpaRepository<PointEntity, UUID> {
    
    /**
     * 비관적 락을 사용한 포인트 조회
     * SELECT ... FOR UPDATE
     * 
     * 용도: 포인트 사용 시 동시성 제어 (잔액 음수 방지)
     * 정렬: 만료일 순 (FIFO 차감)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PointEntity> findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(UUID userId, LocalDateTime now);
    
    /**
     * 락 없는 읽기 전용 조회
     * 
     * 용도: 잔액 조회 (수정 없음)
     */
    @Query("SELECT p FROM PointEntity p WHERE p.userId = :userId AND p.expiredAt > :now ORDER BY p.expiredAt")
    List<PointEntity> findByUserIdAndExpiredAtAfterReadOnly(UUID userId, LocalDateTime now);
}
