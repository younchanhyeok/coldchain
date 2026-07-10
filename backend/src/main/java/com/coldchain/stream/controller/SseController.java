package com.coldchain.stream.controller;

import com.coldchain.stream.SseBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ⚠ 알려진 갭(PR2 시점, PR4=feat/m5-sse-scoping에서 해소 예정): 이 엔드포인트는 이제 인증은
 * 요구하지만(JwtAuthenticationFilter), {@link SseBroadcaster}가 아직 모든 emitter에 전
 * 이벤트를 브로드캐스트한다 — 즉 로그인만 하면 화주 구분 없이 타 화주의 온도·위치·이상탐지·
 * 알림·예측 이벤트가 실시간으로 그대로 넘어간다. REST 스코핑(404 존재 은닉)과 결이 안 맞는
 * 상태를 알고 있고, PR4에서 emitter를 shipperId로 나누고 이벤트를 트래커→화주로 해석해
 * 매칭되는 emitter에만 보내도록 고친다.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class SseController {

    private final SseBroadcaster broadcaster;

    public SseController(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
