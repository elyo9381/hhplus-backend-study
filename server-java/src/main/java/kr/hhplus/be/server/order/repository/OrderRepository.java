package kr.hhplus.be.server.order.repository;

import kr.hhplus.be.server.order.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity,Long> {
}
