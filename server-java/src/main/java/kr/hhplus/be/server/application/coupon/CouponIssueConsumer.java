package kr.hhplus.be.server.application.coupon;

import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatus;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatusRepository;
import kr.hhplus.be.server.domain.coupon.CouponRepository;
import kr.hhplus.be.server.domain.coupon.UserCoupon;
import kr.hhplus.be.server.domain.coupon.UserCouponRepository;
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

    @KafkaListener(topics = "coupon-issue-request", concurrency = "3")
    @Transactional
    public void consume(CouponIssueRequest request) {
        try {
            // 1. 중복 발급 체크
            if (userCouponRepository.existsByUserIdAndCouponId(request.userId(), request.couponId())) {
                updateStatusFailed(request.requestId(), "이미 발급된 쿠폰");
                log.info("중복 발급 차단 - couponId: {}, userId: {}", request.couponId(), request.userId());
                return;
            }

            // 2. 현재 발급 수 조회 (순위 계산용)
            long currentCount = userCouponRepository.countByCouponId(request.couponId());

            // 3. 쿠폰 조회
            Coupon coupon = couponRepository.findById(request.couponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다"));

            // 4. 수량 체크
            if (currentCount >= coupon.getTotalQuantity()) {
                updateStatusFailed(request.requestId(), "쿠폰 소진");
                log.info("쿠폰 마감 - couponId: {}, currentCount: {}", request.couponId(), currentCount);
                return;
            }

            // 5. 발급 (도메인 로직)
            coupon.issue();
            couponRepository.save(coupon);

            // 6. 사용자 쿠폰 저장
            UserCoupon userCoupon = new UserCoupon(request.userId(), coupon);
            userCouponRepository.save(userCoupon);

            // 7. 성공 상태 저장 (순위 포함)
            int rank = (int) currentCount + 1;
            updateStatusSuccess(request.requestId(), rank);

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
