package kr.hhplus.be.server.presentation.product;

public record ProductRequest(
        String name,
        String description,
        Long price,
        Integer stock
) {
}
