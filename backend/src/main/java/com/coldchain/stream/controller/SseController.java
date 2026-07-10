package com.coldchain.stream.controller;

import com.coldchain.auth.AuthenticatedUserProvider;
import com.coldchain.stream.SseBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 인증(JwtAuthenticationFilter, ?token= 쿼리 지원)은 PR2에서 이미 걸렸고, 여기서 화주별
 * 스코핑(PR4)까지 마감한다 — {@link SseBroadcaster#subscribe}에 인증된 화주 id를 넘겨
 * 자기 트래커의 이벤트만 받게 한다.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class SseController {

    private final SseBroadcaster broadcaster;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public SseController(SseBroadcaster broadcaster, AuthenticatedUserProvider authenticatedUserProvider) {
        this.broadcaster = broadcaster;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe(authenticatedUserProvider.shipperId());
    }
}
