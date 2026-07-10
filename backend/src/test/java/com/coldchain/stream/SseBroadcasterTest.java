package com.coldchain.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 순수 로직 단위 테스트 — Spring/Docker 없이 로컬에서 돈다. 실제 SseEmitter.send()는
 * 서블릿 컨테이너 없이 호출하면 항상 예외이므로(초기화 안 됨), 전송 자체가 아니라 라우팅
 * 결과(어느 화주 emitter 목록으로 갔는지)만 검증한다 — emittersFor()가 그 테스트 seam.
 */
class SseBroadcasterTest {

    @Test
    void subscribe_registersEmitterUnderOwningShipperOnly() {
        TrackerOwnerCache cache = mock(TrackerOwnerCache.class);
        when(cache.shipperIdOf("TRK-A")).thenReturn(1L);
        when(cache.shipperIdOf("TRK-B")).thenReturn(2L);
        SseBroadcaster broadcaster = new SseBroadcaster(cache);

        SseEmitter emitterForShipperA = broadcaster.subscribe(1L);
        SseEmitter emitterForShipperB = broadcaster.subscribe(2L);

        assertThat(broadcaster.emittersFor("TRK-A")).containsExactly(emitterForShipperA);
        assertThat(broadcaster.emittersFor("TRK-A")).doesNotContain(emitterForShipperB);
        assertThat(broadcaster.emittersFor("TRK-B")).containsExactly(emitterForShipperB);
        assertThat(broadcaster.emittersFor("TRK-B")).doesNotContain(emitterForShipperA);
    }

    @Test
    void subscribe_multipleEmittersForSameShipper_allRouted() {
        TrackerOwnerCache cache = mock(TrackerOwnerCache.class);
        when(cache.shipperIdOf("TRK-A")).thenReturn(1L);
        SseBroadcaster broadcaster = new SseBroadcaster(cache);

        SseEmitter first = broadcaster.subscribe(1L);
        SseEmitter second = broadcaster.subscribe(1L);

        assertThat(broadcaster.emittersFor("TRK-A")).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void emittersFor_unknownTracker_returnsEmpty() {
        TrackerOwnerCache cache = mock(TrackerOwnerCache.class);
        when(cache.shipperIdOf("TRK-UNKNOWN")).thenReturn(null);
        SseBroadcaster broadcaster = new SseBroadcaster(cache);
        broadcaster.subscribe(1L);

        assertThat(broadcaster.emittersFor("TRK-UNKNOWN")).isEmpty();
    }

    @Test
    void emittersFor_shipperWithNoSubscribers_returnsEmpty() {
        TrackerOwnerCache cache = mock(TrackerOwnerCache.class);
        when(cache.shipperIdOf("TRK-A")).thenReturn(1L);
        SseBroadcaster broadcaster = new SseBroadcaster(cache);
        // 화주 1L은 구독한 적 없음 — subscribe() 호출 없이 바로 조회.

        assertThat(broadcaster.emittersFor("TRK-A")).isEmpty();
    }
}
