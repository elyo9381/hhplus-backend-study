package kr.hhplus.be.server.domain.payment;

import java.util.UUID;

/**
 * Point 외부 시스템과의 통신을 위한 Port 인터페이스
 * 의존성 역전 원칙(DIP)을 위해 Payment 도메인에 정의
 */
public interface PointPort {
    void usePoint(UUID userId, Long amount);
    Long getAvailablePoints(UUID userId);
}
