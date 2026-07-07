package com.coldchain.detection.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 트래커당 최근 온도 윈도우를 Redis List로 관리한다(L2 판정용 캐시 — 재시작 시 소실돼도 무방,
 * 새 리딩이 들어오며 다시 채워짐). TTL로 유휴 트래커의 윈도우를 자연 만료시킨다.
 */
@Component
public class TemperatureWindowRepository {

    private static final int MAX_SIZE = 20;
    private static final Duration TTL = Duration.ofHours(1);
    private static final String KEY_PREFIX = "window:";

    private final StringRedisTemplate redisTemplate;

    public TemperatureWindowRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 새 리딩을 윈도우에 추가하고 갱신된 윈도우(시간순, 최근 {@value #MAX_SIZE}개)를 반환한다.
     * out-of-order 리딩(윈도우의 가장 최근 시각보다 과거)은 push하지 않고 조용히 스킵한다
     * — L1의 낙관적 락 out-of-order 방어와 같은 계열의 방어.
     */
    public List<WindowPoint> pushAndGet(String trackerId, WindowPoint point) {
        String key = KEY_PREFIX + trackerId;
        List<WindowPoint> current = read(key);

        if (!current.isEmpty() && point.epochMillis() <= current.get(current.size() - 1).epochMillis()) {
            return current;
        }

        redisTemplate.opsForList().rightPush(key, serialize(point));
        redisTemplate.opsForList().trim(key, -MAX_SIZE, -1);
        redisTemplate.expire(key, TTL);

        current.add(point);
        if (current.size() > MAX_SIZE) {
            return current.subList(current.size() - MAX_SIZE, current.size());
        }
        return current;
    }

    private List<WindowPoint> read(String key) {
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        List<WindowPoint> points = new ArrayList<>();
        if (raw == null) {
            return points;
        }
        for (String entry : raw) {
            points.add(deserialize(entry));
        }
        return points;
    }

    private String serialize(WindowPoint point) {
        return point.temperature() + "," + point.epochMillis();
    }

    private WindowPoint deserialize(String entry) {
        String[] parts = entry.split(",");
        return new WindowPoint(Double.parseDouble(parts[0]), Long.parseLong(parts[1]));
    }
}
