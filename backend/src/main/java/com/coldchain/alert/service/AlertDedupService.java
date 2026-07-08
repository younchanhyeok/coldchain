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
        String key = key(trackerId, type);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 발송이 실패하면 억제 락을 풀어준다 — 그대로 두면 "실패한 알림이 이후 10분간 재시도 자체를
     * 막는" 꼴이 된다(Slack 장애 중 지속 breach가 조용히 묻힘). dedup은 "성공적으로 알린 뒤
     * 중복 억제"가 목적이지 "실패까지 억제"가 목적이 아니므로, 실패 시 락을 즉시 해제해
     * 다음 전이(BREACH 재전이 등)에서 다시 시도할 수 있게 한다.
     */
    public void release(String trackerId, AlertType type) {
        redisTemplate.delete(key(trackerId, type));
    }

    private String key(String trackerId, AlertType type) {
        return KEY_PREFIX + trackerId + ":" + type;
    }
}
