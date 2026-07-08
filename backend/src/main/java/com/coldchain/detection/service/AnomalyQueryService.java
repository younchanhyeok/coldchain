package com.coldchain.detection.service;

import com.coldchain.detection.domain.AnomalyStatus;
import com.coldchain.detection.dto.AnomalyResponse;
import com.coldchain.detection.repository.AnomalyEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인(tracker 등)이 이상탐지 상태를 조회할 때 쓰는 창구 — 도메인 간 참조는
 * repository 직접 참조가 아니라 이 service 레이어를 통해서만 이뤄진다(CLAUDE.md 컨벤션).
 */
@Service
public class AnomalyQueryService {

    private final AnomalyEventRepository anomalyEventRepository;

    public AnomalyQueryService(AnomalyEventRepository anomalyEventRepository) {
        this.anomalyEventRepository = anomalyEventRepository;
    }

    /** CAUTION 판정용 — 유형(SUDDEN/GRADUAL) 무관하게 활성 이상이 하나라도 있으면 true. */
    public boolean hasActiveAnomaly(String trackerId) {
        return anomalyEventRepository.existsByTrackerIdAndStatus(trackerId, AnomalyStatus.ACTIVE);
    }

    /** 트래커 단건 상세의 activeAnomalies[] — 활성 이상 전부(최신순). */
    public List<AnomalyResponse> findActiveAnomalies(String trackerId) {
        return anomalyEventRepository.findByTrackerIdAndStatusOrderByTsDesc(trackerId, AnomalyStatus.ACTIVE)
                .stream()
                .map(AnomalyResponse::from)
                .toList();
    }
}
