package kr.hhplus.be.server.domain.point;

import kr.hhplus.be.server.entity.point.PointHistoryEntity;

public class PointHistoryMapper {

    public static PointHistory toDomain(PointHistoryEntity entity) {
        return new PointHistory(
                entity.getId(),
                entity.getUserId(),
                entity.getType(),
                entity.getAmount(),
                entity.getBalance(),
                entity.getDescription(),
                entity.getCreatedAt()
        );
    }

    public static PointHistoryEntity toEntity(PointHistory domain) {
        return new PointHistoryEntity(
                domain.getUserId(),
                domain.getType(),
                domain.getAmount(),
                domain.getBalance(),
                domain.getDescription()
        );
    }
}
