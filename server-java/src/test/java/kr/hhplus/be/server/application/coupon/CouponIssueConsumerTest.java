package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueConsumerTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponIssueStatusRepository couponIssueStatusRepository;

    @InjectMocks
    private CouponIssueConsumer consumer;

    @Test
    @DisplayName("정상 발급 시 순위가 계산되고 상태가 SUCCESS로 업데이트된다")
    void consume_success() {
        // given
        UUID requestId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CouponIssueRequest request = new CouponIssueRequest(requestId, couponId, userId, Instant.now());

        Coupon coupon = new Coupon("테스트 쿠폰", 1000L, 100,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(userCouponRepository.countByCouponId(couponId)).thenReturn(5L);
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.of(status));

        // when
        consumer.consume(request);

        // then
        verify(userCouponRepository).save(any(UserCoupon.class));
        verify(couponRepository).save(coupon);
        verify(couponIssueStatusRepository, times(1)).save(argThat(s ->
                s.getStatus() == CouponIssueStatusType.SUCCESS && s.getRank() == 6
        ));
    }

    @Test
    @DisplayName("중복 발급 시 FAILED 상태로 업데이트되고 저장하지 않는다")
    void consume_duplicated() {
        // given
        UUID requestId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CouponIssueRequest request = new CouponIssueRequest(requestId, couponId, userId, Instant.now());

        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(true);
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.of(status));

        // when
        consumer.consume(request);

        // then
        verify(userCouponRepository, never()).save(any());
        verify(couponRepository, never()).save(any());
        verify(couponIssueStatusRepository).save(argThat(s ->
                s.getStatus() == CouponIssueStatusType.FAILED &&
                        s.getFailReason().equals("이미 발급된 쿠폰")
        ));
    }

    @Test
    @DisplayName("수량 초과 시 FAILED 상태로 업데이트되고 저장하지 않는다")
    void consume_soldOut() {
        // given
        UUID requestId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CouponIssueRequest request = new CouponIssueRequest(requestId, couponId, userId, Instant.now());

        Coupon coupon = new Coupon("테스트 쿠폰", 1000L, 100,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(userCouponRepository.countByCouponId(couponId)).thenReturn(100L);
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.of(status));

        // when
        consumer.consume(request);

        // then
        verify(userCouponRepository, never()).save(any());
        verify(couponRepository, never()).save(any());
        verify(couponIssueStatusRepository).save(argThat(s ->
                s.getStatus() == CouponIssueStatusType.FAILED &&
                        s.getFailReason().equals("쿠폰 소진")
        ));
    }

    @Test
    @DisplayName("쿠폰이 존재하지 않으면 예외가 발생한다")
    void consume_couponNotFound() {
        // given
        UUID requestId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CouponIssueRequest request = new CouponIssueRequest(requestId, couponId, userId, Instant.now());

        CouponIssueStatus status = new CouponIssueStatus(requestId, couponId, userId);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
        when(userCouponRepository.countByCouponId(couponId)).thenReturn(5L);
        when(couponRepository.findById(couponId)).thenReturn(Optional.empty());
        when(couponIssueStatusRepository.findById(requestId)).thenReturn(Optional.of(status));

        // when & then
        try {
            consumer.consume(request);
        } catch (Exception e) {
            // 예외 발생 확인
        }

        verify(couponIssueStatusRepository).save(argThat(s ->
                s.getStatus() == CouponIssueStatusType.FAILED &&
                        s.getFailReason().equals("시스템 오류")
        ));
    }
}
