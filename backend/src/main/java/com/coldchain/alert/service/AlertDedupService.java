package com.coldchain.alert.service;

import com.coldchain.alert.domain.AlertType;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * FR-6 "동일 트래커의 반복 발송 억제" — Redis SETNX+TTL로 같은 (tracker, type) 알림을
 * 짧은 시간 안에 다시 발송하지 않게 한다. BREACH/ANOMALY는 키에 type이 포함돼 서로 독립적으로
 * 억제된다(한쪽이 억제 중이어도 다른 쪽은 정상 발송).
 */
@Component
public class AlertDedupService {

    private static final Duration DEDUP_TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "alert:dedup:";

    private final StringRedisTemplate redisTemplate;

    public AlertDedupService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 발송해도 되면 true(억제 락 획득), 이미 억제 중이면 false. */
    public boolean tryAcquire(String trackerId, AlertType type) {
        String key = KEY_PREFIX + trackerId + ":" + type;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return Boolean.TRUE.equals(acquired);
    }
}
