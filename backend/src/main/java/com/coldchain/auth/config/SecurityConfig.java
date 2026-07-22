package com.coldchain.auth.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증 방식 4종 중 Security 필터로 통합하는 것은 JWT 하나뿐이다 —
 * 매직링크(URL path 토큰=capability, principal 개념 없음)·디바이스 키(tracker 행 조회와 결합)·
 * 어드민 키(엔드포인트 1~2개)는 각 컨트롤러 검사를 유지한다(과한 승격 금지).
 *
 * PR1(m5-auth-login)에선 전 경로 permitAll이었다 — 로그인 API만 추가하고 기존 동작을
 * 바꾸지 않기 위함. PR2(m5-shipper-scoping)에서 JwtAuthenticationFilter를 필터 체인에 걸고
 * 화주 경로를 authenticated로 전환한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ProblemDetailAuthEntryPoint entryPoint,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // 전 구간 무상태 Bearer — 세션 쿠키가 없으므로 CSRF 공격 표면 자체가 없다.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        // 매직링크 — 토큰 자체가 리소스 식별자(capability), principal 개념 없음(PR3).
                        .requestMatchers(HttpMethod.GET, "/api/v1/track/*").permitAll()
                        // 디바이스 키(X-Device-Key)로 컨트롤러가 검사 — 같은 경로의 GET(조회, 화주
                        // 스코핑 대상)과 겹치므로 메서드로 구분해야 한다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/trackers/*/readings").permitAll()
                        // 어드민 키(X-Admin-Key)로 컨트롤러가 검사 — 필터는 경로만 통과시키고 키 검증은
                        // 컨트롤러가 한다. GET(지표·평가런 조회) + POST(평가런 수동 생성, M7).
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").permitAll()
                        // metrics·prometheus는 M6 부하테스트 계측용 — 로컬 docker-compose 전용
                        // 배포(D3)라 외부 노출면이 없다. 공개 배포로 바뀌면 어드민 키 검사 승격 필요.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/metrics/**",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
