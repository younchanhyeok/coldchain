package com.coldchain.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * (@)Async 실행기 2종 — 기본 SimpleAsyncTaskExecutor(호출마다 새 스레드)는 M6 부하테스트에서
 * 초당 ~3,000개 스레드를 생성·폐기했고 상한이 없어 bounded 풀로 교체한다.
 *
 * 풀 분리 이유(실측): 예측(L3)은 리딩마다 Python HTTP 호출(타임아웃 2s)을 타는 외부 I/O 작업이라,
 * Python이 포화해 느려지면(브라운아웃 — 죽지 않아서 서킷도 안 열림) 예측 태스크가 스레드를 초 단위로
 * 점유한다. 탐지·알림과 같은 풀을 쓰면 느린 예측이 풀 전체를 잠식해 탐지까지 멈춘다.
 *
 * 거부 정책은 둘 다 discard+카운터. 처음엔 CallerRunsPolicy("유실 없는 백프레셔")로 갔다가
 * 5000 트래커 실측에서 수집 에러율 69%로 되돌아왔다 — 넘친 다운스트림 작업이 수집 스레드에서
 * 실행되며 요청 스레드 풀까지 Python 브라운아웃에 끌려들어간 것. 이 시스템의 불변식은 NFR-3
 * ("예측·알림이 죽어도 수집·저장은 무중단")이므로 과부하 시 올바른 동작은 수집 차단이 아니라
 * 부가기능 드롭이다. 원시 리딩은 항상 저장되므로 탐지·예측은 다음 리딩에서 복구된다.
 * 드롭 없이 다운스트림을 지키면서 수집도 지키려면 프로세스 밖 내구 큐가 필요하다 — M6 Kafka(PR3)의
 * 도입 명분. 드롭 수는 coldchain.async.dropped 카운터(executor 태그)로 계측한다.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private final MeterRegistry meterRegistry;

    public AsyncConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * @Scheduled 전용 스케줄러 — 기본은 단일 스레드라 매시 평가 런 스냅샷(getMetrics 집계)이
     * SseBroadcaster.flushReadings(1초 주기 SSE 방출)·heartbeat·예측 EXPIRE와 같은 스레드에서
     * 순차 실행돼, 무거운 잡 하나가 실시간 방출을 스톨시킨다. 풀로 분리해 서로 독립시킨다.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); // flush(1s) + heartbeat/expire + 시간당 스냅샷이 서로 안 막게
        scheduler.setThreadNamePrefix("sched-");
        return scheduler;
    }

    /** 기본 풀 — 탐지·알림·SSE 적재·예측 무효화 등 짧은 DB/Redis 작업(수 ms). */
    @Override
    public Executor getAsyncExecutor() {
        // 1000rps × 태스크 ~4ms ≈ 평균 동시 4~8 — 16이면 여유. 큐는 순간 버스트 흡수용.
        return build("async-", 16, 2000, "default");
    }

    /** 예측(L3) 전용 풀 — Python 호출(외부 I/O, 최대 2s×2회)이 다른 리스너를 잠식하지 않게 격리.
     *  포화 시 드롭 = "이번 리딩은 예측 스킵"(PredictionClient 쿨다운과 같은 designed outcome). */
    @Bean("predictionExecutor")
    public Executor predictionExecutor() {
        // Python 단일 워커가 처리량 상한 — 풀을 키워봐야 대기만 는다. 큐를 짧게 잡아
        // 오래된 윈도우로 뒷북 예측하는 것보다 빨리 버리고 최신 리딩에서 다시 시도한다.
        return build("predict-", 16, 100, "prediction");
    }

    private Executor build(String threadPrefix, int poolSize, int queueCapacity, String executorTag) {
        Counter dropped = Counter.builder("coldchain.async.dropped")
                .tag("executor", executorTag)
                .description("과부하로 버린 @Async 태스크 수 (NFR-3: 수집을 지키기 위한 드롭)")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadPrefix);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        // 로그는 안 남긴다 — 과부하 상황에서 초당 수천 줄 WARN은 그 자체가 부하. 카운터로 관측.
        executor.setRejectedExecutionHandler((task, pool) -> dropped.increment());
        executor.initialize();
        return executor;
    }
}
