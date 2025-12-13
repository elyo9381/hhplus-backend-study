package kr.hhplus.be.server.infrastructure.outbox.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock 외부 API 서버 (테스트용)
 * 
 * 실제 외부 API를 시뮬레이션
 * - 이벤트 수신
 * - 수신 이력 저장
 * - 조회 API 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@Profile({"dev", "test"})
public class MockExternalApiController {

    private final List<ReceivedEvent> receivedEvents = new ArrayList<>();

    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveEvent(@RequestBody Map<String, Object> request) {
        String eventType = (String) request.get("eventType");
        String payload = (String) request.get("payload");
        Long timestamp = ((Number) request.get("timestamp")).longValue();

        log.info("Received event from Outbox: eventType={}, timestamp={}", eventType, timestamp);

        ReceivedEvent event = new ReceivedEvent(
                eventType,
                payload,
                timestamp,
                LocalDateTime.now()
        );
        receivedEvents.add(event);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Event received",
                "eventType", eventType,
                "receivedAt", LocalDateTime.now().toString()
        ));
    }

    @GetMapping
    public ResponseEntity<List<ReceivedEvent>> getReceivedEvents() {
        return ResponseEntity.ok(receivedEvents);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearEvents() {
        receivedEvents.clear();
        return ResponseEntity.noContent().build();
    }

    public record ReceivedEvent(
            String eventType,
            String payload,
            Long timestamp,
            LocalDateTime receivedAt
    ) {}
}
