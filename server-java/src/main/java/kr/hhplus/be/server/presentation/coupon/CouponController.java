package kr.hhplus.be.server.presentation.coupon;

import kr.hhplus.be.server.application.coupon.CouponService;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatus;
import kr.hhplus.be.server.domain.coupon.CouponIssueStatusType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 쿠폰 발급 요청 (비동기)
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @PathVariable UUID couponId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        UUID requestId = couponService.issueCoupon(couponId, userId);
        
        return ResponseEntity.accepted()
                .body(new CouponIssueResponse(requestId, CouponIssueStatusType.PENDING));
    }

    /**
     * 발급 상태 조회
     */
    @GetMapping("/issue-requests/{requestId}")
    public ResponseEntity<CouponIssueStatusResponse> getIssueStatus(
            @PathVariable UUID requestId
    ) {
        CouponIssueStatus status = couponService.getIssueStatus(requestId);
        
        return ResponseEntity.ok(
                new CouponIssueStatusResponse(
                        status.getStatus(),
                        status.getRank(),
                        status.getFailReason()
                )
        );
    }
}
