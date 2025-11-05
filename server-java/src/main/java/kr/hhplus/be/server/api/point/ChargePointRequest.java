package kr.hhplus.be.server.api.point;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class ChargePointRequest {
    private BigDecimal amount;
}
