package com.coldchain.prediction.service;

import com.coldchain.prediction.domain.EvaluationRun;
import com.coldchain.prediction.domain.EvaluationRun.TriggerType;
import com.coldchain.prediction.dto.EvaluationRunResponse;
import com.coldchain.prediction.dto.PredictionMetricsResponse;
import com.coldchain.prediction.repository.EvaluationRunRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 평가 런 — 예측 지표(PredictionMetricsService)의 시점 스냅샷을 상시 기록(M7). M4의 온디맨드
 * 계산을 "런" 단위로 남겨 v1 vs v2 비교와 추이를 만든다. 지표 수학은 getMetrics를 그대로 재사용하고
 * (중복 계산 없음), 여기선 스냅샷 저장과 스케줄만 담당한다.
 */
@Service
public class EvaluationRunService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunService.class);

    private final PredictionMetricsService predictionMetricsService;
    private final PredictionRepository predictionRepository;
    private final EvaluationRunRepository evaluationRunRepository;

    public EvaluationRunService(PredictionMetricsService predictionMetricsService,
            PredictionRepository predictionRepository, EvaluationRunRepository evaluationRunRepository) {
        this.predictionMetricsService = predictionMetricsService;
        this.predictionRepository = predictionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
    }

    /** 수동 런 — 비교 스크립트(PR5)가 페이즈별로 라벨을 달아 스냅샷을 남긴다. modelVersion null = 전체 합산. */
    public EvaluationRunResponse createManual(Instant from, Instant to, String label, String modelVersion) {
        PredictionMetricsResponse metrics = predictionMetricsService.getMetrics(from, to, modelVersion);
        EvaluationRun run = evaluationRunRepository.save(
                EvaluationRun.snapshot(label, TriggerType.MANUAL, metrics));
        return EvaluationRunResponse.from(run);
    }

    public List<EvaluationRunResponse> list(int limit) {
        return evaluationRunRepository.findByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(EvaluationRunResponse::from)
                .toList();
    }

    /**
     * 직전 1시간 창의 지표를 모델버전별로 상시 집계. 창은 [정시-1h, 정시) 반개구간으로 고정해
     * 겹침·경계 중복이 없다. 에피소드가 없는 버전은 스냅샷하지 않고(빈 런 도배 방지),
     * 이미 있는 (기간,버전) SCHEDULED 런은 건너뛴다(재기동·중복 발화 멱등 — 부분 유니크 인덱스가 backstop).
     */
    @Scheduled(cron = "${app.evaluation.cron:0 5 * * * *}")
    public void snapshotPreviousHour() {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        snapshotScheduled(to.minus(1, ChronoUnit.HOURS), to);
    }

    /** 주어진 창의 모델버전별 SCHEDULED 스냅샷. 창을 파라미터로 받아 결정적으로 테스트 가능
     *  (스케줄 메서드는 now 기준 직전 1시간으로 이걸 호출). 이미 있는 (기간,버전) 런은 건너뛴다. */
    public void snapshotScheduled(Instant from, Instant to) {
        for (String version : predictionRepository.findDistinctModelVersions(from, to)) {
            if (evaluationRunRepository.existsByPeriodStartAndPeriodEndAndModelVersionAndTriggerType(
                    from, to, version, TriggerType.SCHEDULED)) {
                continue;
            }
            PredictionMetricsResponse metrics = predictionMetricsService.getMetrics(from, to, version);
            evaluationRunRepository.save(EvaluationRun.snapshot(null, TriggerType.SCHEDULED, metrics));
            log.info("평가 런 스냅샷: {} [{}~{}] TP={} FP={} missed={}",
                    version, from, to, metrics.truePositives(), metrics.falsePositives(), metrics.missedBreaches());
        }
    }
}
