package kr.hhplus.be.server.application.kafka;


import kr.hhplus.be.server.application.outbox.MessageProducer;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
/**
 * MessageProducer를 구현한 다른 구현체는 무시하기 위한  @Primary
 * TODO MessageProducer를 컴포지션으로 사용 클래스에서 list로 처리하도록 변경할것
 * WebClientMessageProducer 이게 사용중이지 않지만, 어디선가 사용할 가능성이 존재함으로 나둠
 * 리팩토링 필요
 *
 */
@Primary

public class KafkaOutboxProducer implements MessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaOutboxProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String eventType, String payload) {
        String topic = resolveTopic(eventType);
        try {
            kafkaTemplate.send(topic, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka send interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka send failed", e.getCause());
        }
    }

    private String resolveTopic(String eventType){
        return eventType.toLowerCase().replace("_","-");
    }
}
