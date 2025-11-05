package kr.hhplus.be.server.api.order;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {
    private UUID userId;
    private List<OrderItemRequest> items;

    @Getter
    @NoArgsConstructor
    public static class OrderItemRequest {
        private UUID productId;
        private Integer quantity;
    }
}
