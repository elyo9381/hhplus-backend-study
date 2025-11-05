package kr.hhplus.be.server.api.point;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PointResponse {
    private UUID userId;
    private BigDecimal balance;
    private LocalDateTime updatedAt;
}
