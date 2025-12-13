package kr.hhplus.be.server.point;

import kr.hhplus.be.server.infrastructure.point.persistence.PointEntity;
import kr.hhplus.be.server.infrastructure.point.persistence.PointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PointRepositoryTest {

    @Autowired
    private PointRepository pointRepository;

    @Test
    void shouldSaveAndFindPoint() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 10000L, LocalDateTime.now().plusYears(1));

        // when
        PointEntity saved = pointRepository.save(point);
        PointEntity found = pointRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getAmount()).isEqualTo(10000L);
    }

    @Test
    void shouldFindNonExpiredPoints() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        // 만료되지 않은 포인트
        pointRepository.save(new PointEntity(userId, 5000L, now.plusDays(10)));
        pointRepository.save(new PointEntity(userId, 3000L, now.plusDays(5)));
        
        // 만료된 포인트
        pointRepository.save(new PointEntity(userId, 2000L, now.minusDays(1)));

        // when
        List<PointEntity> points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(userId, now);

        // then
        assertThat(points).hasSize(2);
        assertThat(points.stream().mapToLong(PointEntity::getAmount).sum()).isEqualTo(8000L);
    }

    @Test
    void shouldOrderByExpiredAtAsc() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        PointEntity point1 = pointRepository.save(new PointEntity(userId, 1000L, now.plusDays(10)));
        PointEntity point2 = pointRepository.save(new PointEntity(userId, 2000L, now.plusDays(5)));
        PointEntity point3 = pointRepository.save(new PointEntity(userId, 3000L, now.plusDays(15)));

        // when
        List<PointEntity> points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(userId, now);

        // then
        assertThat(points).hasSize(3);
        assertThat(points.get(0).getAmount()).isEqualTo(2000L); // 5일 후 만료
        assertThat(points.get(1).getAmount()).isEqualTo(1000L); // 10일 후 만료
        assertThat(points.get(2).getAmount()).isEqualTo(3000L); // 15일 후 만료
    }

    @Test
    void shouldReturnEmptyListWhenNoPoints() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        List<PointEntity> points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(
                userId, LocalDateTime.now()
        );

        // then
        assertThat(points).isEmpty();
    }

    @Test
    void shouldApplyPessimisticLock() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        pointRepository.save(new PointEntity(userId, 10000L, now.plusDays(10)));

        // when
        // 비관적 락이 적용된 메서드 호출
        // SQL 로그에서 "for update" 확인 가능
        List<PointEntity> points = pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(userId, now);

        // then
        assertThat(points).hasSize(1);
        // Note: 실제 락 동작은 멀티 스레드 통합 테스트에서 확인 필요
    }
}
