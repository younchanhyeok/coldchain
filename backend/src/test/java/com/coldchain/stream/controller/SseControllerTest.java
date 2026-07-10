package com.coldchain.stream.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SSE 인증 계약 — 화주별 라우팅 로직 자체는 SseBroadcasterTest(단위)가 검증하고, 여기서는
 * 필터 체인이 실제로 이 경로에 걸려 있는지(무토큰 401)와 두 토큰 전달 방식(Authorization
 * 헤더, ?token= 쿼리 — EventSource가 헤더 미지원이라 필요) 모두 인증을 통과하는지만 본다.
 * DefaultShipperAuthConfig를 일부러 import하지 않는다 — "무토큰" 케이스를 명확히 하기 위함.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void stream_withoutToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stream_withBearerHeader_opensAsyncStream() throws Exception {
        String token = jwtTokenProvider.createAccessToken(1L, AppUserRole.SHIPPER);

        mockMvc.perform(get("/api/v1/stream").header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted());
    }

    @Test
    void stream_withQueryToken_opensAsyncStream() throws Exception {
        String token = jwtTokenProvider.createAccessToken(1L, AppUserRole.SHIPPER);

        mockMvc.perform(get("/api/v1/stream").param("token", token))
                .andExpect(request().asyncStarted());
    }
}
