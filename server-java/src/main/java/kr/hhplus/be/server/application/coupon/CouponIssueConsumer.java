package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatus;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatusRepository;
import kr.hhplus.be.server.domain.coupon.CouponRepository;
import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.domain.coupon.UserCouponRepository;
import kr.hhplus.be.server.infrastructure.coupon.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueStatusRepository couponIssueStatusRepository;
    private final CouponRedisRepository couponRedisRepository;

    @KafkaListener(topics = "coupon-issue-request", concurrency = "3", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(CouponIssueRequest request) {
        log.info("=== Consumer 시작 - requestId: {}, couponId: {}, userId: {} ===",
                request.requestId(), request.couponId(), request.userId());

        try {
            // 1. 중복 발급 체크
            if (userCouponRepository.existsByUserIdAndCouponId(request.userId(), request.couponId())) {
                updateStatusFailed(request.requestId(), "이미 발급된 쿠폰");
                log.info("중복 발급 차단 - couponId: {}, userId: {}", request.couponId(), request.userId());
                return;
            }

            // 2. 쿠폰 조회
            Coupon coupon = couponRepository.findById(request.couponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

            // 3. Redis INCR로 순위 원자적 획득 (분산 환경에서도 중복 없음)
            long rank = couponRedisRepository.incrementRank(request.couponId());

            // 4. 수량 체크 (순위가 총 수량을 초과하면 실패)
            if (rank > coupon.getTotalQuantity()) {
                updateStatusFailed(request.requestId(), "쿠폰 소진");
                log.info("쿠폰 마감 - couponId: {}, rank: {}, totalQuantity: {}",
                        request.couponId(), rank, coupon.getTotalQuantity());
                return;
            }

            // 5. 발급 (도메인 로직)
            coupon.issue();
            couponRepository.save(coupon);

            // 6. 사용자 쿠폰 저장
            UserCoupon userCoupon = new UserCoupon(request.userId(), coupon);
            userCouponRepository.save(userCoupon);

            // 7. 성공 상태 저장 (순위 포함)
            updateStatusSuccess(request.requestId(), (int) rank);

            log.info("쿠폰 발급 성공 - rank: {}, couponId: {}, userId: {}, remaining: {}",
                    rank, request.couponId(), request.userId(), coupon.getRemainingQuantity());

        } catch (IllegalStateException e) {
            updateStatusFailed(request.requestId(), e.getMessage());
            log.warn("쿠폰 발급 실패 - couponId: {}, userId: {}, reason: {}",
                    request.couponId(), request.userId(), e.getMessage());
        } catch (Exception e) {
            updateStatusFailed(request.requestId(), "시스템 오류");
            log.error("쿠폰 발급 중 오류 - couponId: {}, userId: {}",
                    request.couponId(), request.userId(), e);
            throw e;
        }
    }

    private void updateStatusSuccess(UUID requestId, int rank) {
        CouponIssueStatus status = couponIssueStatusRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다"));
        status.updateSuccess(rank);
        couponIssueStatusRepository.save(status);
    }

    private void updateStatusFailed(UUID requestId, String failReason) {
        CouponIssueStatus status = couponIssueStatusRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("요청을 찾을 수 없습니다"));
        status.updateFailed(failReason);
        couponIssueStatusRepository.save(status);
    }
}
