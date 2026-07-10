package com.coldchain.auth;

import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.tracker.domain.Tracker;
import com.coldchain.tracker.repository.TrackerRepository;
import org.springframework.stereotype.Component;

/**
 * 트래커 하위 읽기 엔드포인트(readings/anomalies/prediction/track, 트래커 상세)에 소유권 검사를
 * 더한다 — 컨트롤러 층에서 호출한다(서비스에 넣으면 수령기관 매직링크 경로가 재사용하는
 * TrackService 같은 서비스까지 화주 가드가 잘못 걸린다, PR3 참고).
 *
 * 부재와 타사 소유 모두 동일한 404 — 스코프 위반을 403이 아니라 404로 감춘다(API 명세).
 */
@Component
public class TrackerOwnershipGuard {

    private final TrackerRepository trackerRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TrackerOwnershipGuard(TrackerRepository trackerRepository,
            AuthenticatedUserProvider authenticatedUserProvider) {
        this.trackerRepository = trackerRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public void assertOwnedByCurrentShipper(String trackerId) {
        Tracker tracker = trackerRepository.findById(trackerId)
                .orElseThrow(() -> notFound(trackerId));
        if (!tracker.getShipperId().equals(authenticatedUserProvider.shipperId())) {
            throw notFound(trackerId);
        }
    }

    private ResourceNotFoundException notFound(String trackerId) {
        return new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId);
    }
}
