package kr.hhplus.be.server.application.outbox;

/**
 * 외부 메시지 시스템과의 통신을 위한 Port 인터페이스
 * 
 * 구현체:
 * - MockMessageProducer: 테스트용
 * - KafkaMessageProducer: 실제 Kafka 발행 (추후 구현)
 */
public interface MessageProducer {
    void send(String eventType, String payload);
}
