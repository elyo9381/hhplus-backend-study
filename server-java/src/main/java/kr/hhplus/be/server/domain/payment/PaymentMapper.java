package kr.hhplus.be.server.domain.payment;

import kr.hhplus.be.server.entity.payment.PaymentEntity;
import kr.hhplus.be.server.domain.payment.PaymentType;

public class PaymentMapper {

    public static Payment toDomain(PaymentEntity entity) {
        return new Payment(
                entity.getId(),
                entity.getOrderId(),
                entity.getAmount(),
                entity.getPointAmount(),
                entity.getType(),
                entity.getStatus(),
                entity.getReason(),
                entity.getPaidAt(),
                entity.getCreatedAt()
        );
    }

    public static PaymentEntity toEntity(Payment domain) {
        return new PaymentEntity(
                domain.getOrderId(),
                domain.getAmount(),
                domain.getPointAmount(),
                domain.getType()
        );
    }
}
