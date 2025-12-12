package kr.hhplus.be.server.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PointController.class)
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointService pointService;

    @Test
    void shouldGetPoints() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        when(pointService.getAvailablePoints(userId)).thenReturn(15000L);

        // when & then
        mockMvc.perform(get("/api/points/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(15000));
    }

    @Test
    void shouldReturnZeroWhenNoPoints() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        when(pointService.getAvailablePoints(userId)).thenReturn(0L);

        // when & then
        mockMvc.perform(get("/api/points/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void shouldChargePoint() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        Long amount = 10000L;
        
        PointEntity chargedPoint = new PointEntity(userId, amount, LocalDateTime.now().plusYears(1));
        when(pointService.chargePoint(eq(userId), eq(amount))).thenReturn(chargedPoint);
        when(pointService.getAvailablePoints(userId)).thenReturn(10000L);

        String requestBody = """
                {
                    "amount": 10000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/points/{userId}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(10000));
    }
}
