package kr.hhplus.be.server.infrastructure.outbox.message;

import kr.hhplus.be.server.application.outbox.MessageProducer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 Mock Message Producer
 * 
 * 실제로 외부 시스템에 발행하지 않고 메모리에 기록만 함
 * 테스트에서 메시지 발행 여부 검증 가능
 */
@Component
public class MockMessageProducer implements MessageProducer {

    @Getter
    private final List<Message> sentMessages = new ArrayList<>();

    @Override
    public void send(String eventType, String payload) {
        Message message = new Message(eventType, payload, LocalDateTime.now());
        sentMessages.add(message);
        System.out.println("[MockMessageProducer] Sent: " + eventType);
    }

    public void clear() {
        sentMessages.clear();
    }

    @Getter
    public static class Message {
        private final String eventType;
        private final String payload;
        private final LocalDateTime sentAt;

        public Message(String eventType, String payload, LocalDateTime sentAt) {
            this.eventType = eventType;
            this.payload = payload;
            this.sentAt = sentAt;
        }
    }
}
