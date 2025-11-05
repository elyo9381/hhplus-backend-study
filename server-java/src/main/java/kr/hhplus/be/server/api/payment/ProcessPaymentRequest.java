package kr.hhplus.be.server.api.payment;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProcessPaymentRequest {
    private UUID orderId;
    private BigDecimal pointAmount;
}
