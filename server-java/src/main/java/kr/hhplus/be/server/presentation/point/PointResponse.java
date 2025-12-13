package kr.hhplus.be.server.presentation.point;

import java.util.UUID;

public record PointResponse(
        UUID userId,
        Long balance
) {
    public static PointResponse of(UUID userId, Long balance) {
        return new PointResponse(userId, balance);
    }
}
