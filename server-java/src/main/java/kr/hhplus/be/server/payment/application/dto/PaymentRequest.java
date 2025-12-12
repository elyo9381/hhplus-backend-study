package kr.hhplus.be.server.payment.application.dto;

import java.util.UUID;

public record PaymentRequest(
        UUID userId
) {
}
