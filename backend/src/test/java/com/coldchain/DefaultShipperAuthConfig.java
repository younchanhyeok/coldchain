package com.coldchain;

import com.coldchain.auth.domain.AppUserRole;
import com.coldchain.auth.service.JwtTokenProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M5 이전 기존 통합 테스트들은 화주 스코핑을 전제하지 않고 작성됐다(트래커를 shipperId=1L로
 * 시드). authenticated() 전환 후 이 테스트들이 목적(각자 도메인 로직)과 무관하게 401로만
 * 깨지는 것을 막기 위해, MockMvcBuilderCustomizer로 화주A(V8 시드, id=1) 토큰을 모든 요청의
 * 기본 Authorization 헤더로 주입한다. 화주 간 교차 접근을 검증하는 테스트는 별도
 * AuthorizationIntegrationTest에서 명시적으로 다른 토큰을 헤더로 덮어써 다룬다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DefaultShipperAuthConfig {

    public static final Long SHIPPER_A_ID = 1L;
    public static final Long SHIPPER_B_ID = 2L;

    @Bean
    public MockMvcBuilderCustomizer defaultShipperAuthCustomizer(JwtTokenProvider jwtTokenProvider) {
        String token = jwtTokenProvider.createAccessToken(SHIPPER_A_ID, AppUserRole.SHIPPER);
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").header("Authorization", "Bearer " + token));
    }
}
