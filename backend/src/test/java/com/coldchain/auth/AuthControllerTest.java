package com.coldchain.auth;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coldchain.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인/refresh 계약 검증 — V8 시드 계정(shipper-a/coldchain-a)을 그대로 사용한다.
 * 계정 존재 은닉: 없는 이메일과 틀린 비밀번호가 같은 401 body를 반환해야 한다(명세 §2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_success_returnsTokensAndProfile() throws Exception {
        mockMvc.perform(login("shipper-a@coldchain.local", "coldchain-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("SHIPPER"))
                .andExpect(jsonPath("$.companyName").value("한국제약"));
    }

    @Test
    void login_unknownEmailAndWrongPassword_returnIdenticalUnauthorizedBody() throws Exception {
        String unknownEmail = mockMvc.perform(login("no-such-user@coldchain.local", "whatever"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(login("shipper-a@coldchain.local", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // timestamp만 다르고 나머지(code·detail·status)는 완전히 같아야 한다 — 계정 존재 은닉.
        JsonNode a = objectMapper.readTree(unknownEmail);
        JsonNode b = objectMapper.readTree(wrongPassword);
        org.assertj.core.api.Assertions.assertThat(a.get("code")).isEqualTo(b.get("code"));
        org.assertj.core.api.Assertions.assertThat(a.get("detail")).isEqualTo(b.get("detail"));
        org.assertj.core.api.Assertions.assertThat(a.get("status")).isEqualTo(b.get("status"));
        org.assertj.core.api.Assertions.assertThat(a.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void refresh_success_returnsNewPair() throws Exception {
        MvcResult loginResult = mockMvc.perform(login("shipper-a@coldchain.local", "coldchain-a"))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.companyName").value("한국제약"));
    }

    @Test
    void refresh_withAccessToken_unauthorized() throws Exception {
        MvcResult loginResult = mockMvc.perform(login("shipper-a@coldchain.local", "coldchain-a"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refresh_withTamperedToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.tampered.signature\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void login_responseNeverContainsPasswordHash() throws Exception {
        mockMvc.perform(login("shipper-a@coldchain.local", "coldchain-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").value(not("")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
