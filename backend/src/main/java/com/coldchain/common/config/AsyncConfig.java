package com.coldchain.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * (@)Async 기본 실행기를 bounded 풀로 교체 — 기본 SimpleAsyncTaskExecutor는 호출마다 새
 * 스레드를 만들어, M6 부하테스트(5000 트래커=1000rps)에서 초당 ~3,000개(리스너 3종×rps)의
 * 스레드를 생성·폐기했다. 살아있는 스레드 수는 태스크가 짧아 200대에 머물렀지만 생성 비용
 * 자체가 낭비고, 유입이 조금만 더 몰리면 상한 없이 폭발하는 구조라 안정성 결함이다.
 *
 * 포화 시 CallerRunsPolicy — 이벤트를 버리지 않고(탐지·알림은 유실 불가) 발행자 스레드로
 * 역류시킨다. 수집 스레드가 느려지는 형태로 백프레셔가 겉으로 드러나며, 이는 부하테스트
 * 지표(수집 p99)에서 관측 가능하다 — 조용한 유실보다 낫다.
 *
 * 순서 주의: 풀 전환 후에도 같은 트래커의 이벤트가 서로 다른 스레드에 배정되는 것은 동일하므로
 * AnomalyDetectionService의 트래커별 직렬화 락은 여전히 필요하다(제거는 Kafka 파티션 전환 때).
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("async-");
        // 리스너 태스크는 DB/Redis 왕복이 대부분(수 ms) — 커넥션 풀(30)보다 크게 잡으면
        // 커넥션 대기 스레드만 늘어난다. 코어를 상한과 같게 두어 큐가 쌓이기 전에 즉시 확장.
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
