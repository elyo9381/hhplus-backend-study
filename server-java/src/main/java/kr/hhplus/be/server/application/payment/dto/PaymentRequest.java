package kr.hhplus.be.server.application.payment.dto;

import java.util.UUID;

public record PaymentRequest(
        UUID userId
) {
}
