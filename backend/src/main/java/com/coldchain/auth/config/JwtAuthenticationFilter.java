package com.coldchain.auth.config;

import com.coldchain.auth.service.JwtTokenProvider;
import com.coldchain.common.error.AuthUnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더(또는 SSE 전용 ?token= 쿼리 — EventSource가 헤더를 지원하지
 * 않음)에서 access 토큰을 읽어 SecurityContext에 채운다. 토큰이 없거나 무효해도 여기서
 * 예외를 던지지 않는다 — 그냥 미인증 상태로 다음 필터로 넘기고, 그 경로가 permitAll이 아니면
 * authorizeHttpRequests가 거부해 ProblemDetailAuthEntryPoint가 401을 낸다(permitAll 경로에
 * 우연히 실린 무효 토큰 때문에 매직링크·디바이스 키 요청까지 막히면 안 된다).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String STREAM_PATH = "/api/v1/stream";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                JwtTokenProvider.TokenPayload payload = jwtTokenProvider.parseAccessToken(token);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + payload.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(payload.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthUnauthorizedException e) {
                // 미인증 상태로 그대로 진행 — 위 클래스 주석 참고.
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        if (STREAM_PATH.equals(request.getRequestURI())) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
    }
}
