package com.coldchain.stream;

import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * SSE 이벤트(trackerId 기준)를 어느 화주의 emitter에 보낼지 정하기 위한 trackerId→shipperId
 * 조회 캐시. tracker.shipper_id는 생성 후 불변이라 무효화가 필요 없다 — 리딩마다 DB를 다시
 * 묻지 않기 위한 순수 캐시(트래커 수천 개 규모에서 메모리 무해, PR2의 TrackerOwnershipGuard
 * 설계와 같은 절충).
 */
@Component
public class TrackerOwnerCache {

    private final TrackerRepository trackerRepository;
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

    public TrackerOwnerCache(TrackerRepository trackerRepository) {
        this.trackerRepository = trackerRepository;
    }

    /** 트래커가 없으면 null — 이벤트 발행 시점엔 이론상 항상 존재하지만, 방어적으로 처리한다. */
    public Long shipperIdOf(String trackerId) {
        Long cached = cache.get(trackerId);
        if (cached != null) {
            return cached;
        }
        return trackerRepository.findById(trackerId)
                .map(Tracker::getShipperId)
                .map(shipperId -> {
                    cache.put(trackerId, shipperId);
                    return shipperId;
                })
                .orElse(null);
    }
}
