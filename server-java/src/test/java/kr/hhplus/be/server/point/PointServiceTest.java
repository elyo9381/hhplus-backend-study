package kr.hhplus.be.server.point;

import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.infrastructure.point.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointService pointService;

    @Test
    void shouldChargePoint() {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 10000L;
        
        PointEntity savedPoint = new PointEntity(userId, amount, LocalDateTime.now().plusYears(1));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(pointRepository.save(any(PointEntity.class))).thenReturn(savedPoint);

        // when
        PointEntity result = pointService.chargePoint(userId, amount);

        // then
        assertThat(result.getAmount()).isEqualTo(amount);
        verify(pointRepository).save(any(PointEntity.class));
        verify(pointHistoryRepository).save(any(PointHistoryEntity.class));
    }

    @Test
    void shouldSaveChargeHistory() {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 5000L;
        
        PointEntity existingPoint = new PointEntity(userId, 10000L, LocalDateTime.now().plusYears(1));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(existingPoint));
        when(pointRepository.save(any(PointEntity.class)))
                .thenReturn(new PointEntity(userId, amount, LocalDateTime.now().plusYears(1)));

        // when
        pointService.chargePoint(userId, amount);

        // then
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).save(captor.capture());
        
        PointHistoryEntity history = captor.getValue();
        assertThat(history.getType()).isEqualTo(PointHistoryType.CHARGE);
        assertThat(history.getAmount()).isEqualTo(amount);
        assertThat(history.getBalance()).isEqualTo(15000L); // 10000 + 5000
    }

    @Test
    void shouldGetAvailablePoints() {
        // given
        UUID userId = UUID.randomUUID();
        List<PointEntity> points = List.of(
                new PointEntity(userId, 10000L, LocalDateTime.now().plusDays(10)),
                new PointEntity(userId, 5000L, LocalDateTime.now().plusDays(5))
        );
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(points);

        // when
        Long balance = pointService.getAvailablePoints(userId);

        // then
        assertThat(balance).isEqualTo(15000L);
    }

    @Test
    void shouldReturnZeroWhenNoPoints() {
        // given
        UUID userId = UUID.randomUUID();
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        Long balance = pointService.getAvailablePoints(userId);

        // then
        assertThat(balance).isEqualTo(0L);
    }

    @Test
    void shouldUsePointFromSinglePoint() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 10000L, LocalDateTime.now().plusDays(10));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(point));

        // when
        pointService.usePoint(userId, 3000L);

        // then
        assertThat(point.getAmount()).isEqualTo(7000L);
        verify(pointHistoryRepository).save(any(PointHistoryEntity.class));
    }

    @Test
    void shouldUsePointFromMultiplePointsInOrder() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point1 = new PointEntity(userId, 3000L, LocalDateTime.now().plusDays(5));
        PointEntity point2 = new PointEntity(userId, 7000L, LocalDateTime.now().plusDays(10));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(point1, point2));

        // when
        pointService.usePoint(userId, 5000L);

        // then
        assertThat(point1.getAmount()).isEqualTo(0L);     // 3000 모두 사용
        assertThat(point2.getAmount()).isEqualTo(5000L);  // 2000 사용 (7000 - 2000)
    }

    @Test
    void shouldThrowExceptionWhenInsufficientBalance() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 5000L, LocalDateTime.now().plusDays(10));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(point));

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, 10000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient point balance");
    }

    @Test
    void shouldSaveUseHistory() {
        // given
        UUID userId = UUID.randomUUID();
        PointEntity point = new PointEntity(userId, 10000L, LocalDateTime.now().plusDays(10));
        when(pointRepository.findByUserIdAndExpiredAtAfterOrderByExpiredAtAsc(eq(userId), any(LocalDateTime.class)))
                .thenReturn(List.of(point));

        // when
        pointService.usePoint(userId, 3000L);

        // then
        ArgumentCaptor<PointHistoryEntity> captor = ArgumentCaptor.forClass(PointHistoryEntity.class);
        verify(pointHistoryRepository).save(captor.capture());
        
        PointHistoryEntity history = captor.getValue();
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getAmount()).isEqualTo(3000L);
        assertThat(history.getBalance()).isEqualTo(7000L); // 10000 - 3000
    }
}
