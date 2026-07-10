package com.coldchain.auth.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증 방식 4종 중 Security 필터로 통합하는 것은 JWT 하나뿐이다 —
 * 매직링크(URL path 토큰=capability, principal 개념 없음)·디바이스 키(tracker 행 조회와 결합)·
 * 어드민 키(엔드포인트 1~2개)는 각 컨트롤러 검사를 유지한다(과한 승격 금지).
 *
 * PR1(m5-auth-login) 시점엔 전 경로 permitAll — 로그인 API만 추가하고 기존 동작을 바꾸지
 * 않는다. 화주 경로의 authenticated 전환은 PR2(m5-shipper-scoping)에서 JwtAuthenticationFilter와
 * 함께 켠다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ProblemDetailAuthEntryPoint entryPoint)
            throws Exception {
        http
                // 전 구간 무상태 Bearer — 세션 쿠키가 없으므로 CSRF 공격 표면 자체가 없다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS는 기존 WebConfig(WebMvcConfigurer)에서 여기로 이전 — Security 필터 체인이 preflight를
     * 먼저 보므로 설정 소스를 한 곳으로 통일한다(둘로 나뉘면 인증 전환 시 preflight가 401로 막히는
     * 사고가 남).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
