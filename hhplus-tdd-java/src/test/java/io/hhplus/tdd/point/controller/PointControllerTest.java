package io.hhplus.tdd.point.controller;

import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 테스트
 * 작성 이유 :
 * http 응답에 대한 성공값 , 실패값을 확인하기 위해서
 */
@WebMvcTest(PointController.class)
class PointControllerTest {

    private static final Logger log = LoggerFactory.getLogger(PointControllerTest.class);
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService service;

    @Test
    @DisplayName("충전 성공 후 응답")
    void charge_success() throws Exception {
        // given
        long userId = 1L;
        UserPoint expected = new UserPoint(userId, 1000L, System.currentTimeMillis());
        when(service.charge(userId, 1000L)).thenReturn(expected);

        // when & then
        mockMvc.perform(patch("/point/{id}/charge", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.point").value(1000));
    }

    @Test
    @DisplayName("잘못된 요청 - Content-Type 없음")
    void charge_without_contentType() throws Exception {
        mockMvc.perform(patch("/point/{id}/charge", 1L)
                        .content("1000"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("잘못된 요청 - Body 없음")
    void charge_without_body() throws Exception {
        mockMvc.perform(patch("/point/{id}/charge", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("잘못된 요청 - 잘못된 JSON")
    void charge_with_invalid_json() throws Exception {
        mockMvc.perform(patch("/point/{id}/charge", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid"))
                .andExpect(status().isBadRequest());  // 400
    }

    @Test
    @DisplayName("비즈니스 예외 - 포인트 부족")
    void use_insufficient_point() throws Exception {
        when(service.use(1L, 1000L))
                .thenThrow(new IllegalArgumentException("포인트가 부족합니다"));

        mockMvc.perform(patch("/point/{id}/use", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("포인트가 부족합니다"));
    }

    @Test
    @DisplayName("PathVariable 검증")
    void charge_with_invalid_pathVariable() throws Exception {
        mockMvc.perform(patch("/point/abc/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("1000"))
                .andExpect(status().isBadRequest());  // 400
    }

    // 7. GET 요청 테스트
    @Test
    void retrieve_point_success() throws Exception {
        UserPoint expected = new UserPoint(1L, 1000L, System.currentTimeMillis());
        when(service.retrievPoints(1L)).thenReturn(expected);

        mockMvc.perform(get("/point/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.point").value(1000));
    }

    @Test
    @DisplayName("히스토리 조회 - 빈 리스트")
    void retrieve_history_empty() throws Exception {
        when(service.retrievePointHistroies(1L)).thenReturn(List.of());

        mockMvc.perform(get("/point/{id}/histories", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }


    @Test
    @DisplayName("충전 중 알수없는 Exception 발생시")
    void exception_excute_unknow() throws Exception {
        when(service.charge(1L, -1000L))
                .thenThrow(new NullPointerException("예상치 못한 오류"));

        mockMvc.perform(patch("/point/{id}/charge", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("-1000"))
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("에러가 발생했습니다."));
    }
}