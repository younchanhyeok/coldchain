package com.coldchain.prediction.service;

import com.coldchain.common.error.SemanticInvalidException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        if (from == null || to == null || from.isAfter(to)) {
            throw new SemanticInvalidException("평가 런 기간이 올바르지 않습니다(from ≤ to 필요): " + from + " ~ " + to);
        }
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
     * 직전 1시간 창의 지표를 모델버전별로 상시 집계. 창은 [정시-1h, 정시]로 고정한다.
     * 집계 경계는 getMetrics의 SQL BETWEEN(폐구간)이라, 정확히 정시에 생성된 예측(나노초 단위
     * 타임스탬프라 실측상 거의 없음)은 인접 두 스냅샷에 이중 계상될 수 있다 — 실 데이터에선
     * 무시 가능하고, v1 vs v2 비교(PR5)는 분리된 페이즈 창을 써서 무관하다.
     * 에피소드 없는 버전은 스냅샷 안 함(빈 런 도배 방지), 이미 있는 (기간,버전) 런은 건너뛴다.
     */
    @Scheduled(cron = "${app.evaluation.cron:0 5 * * * *}")
    public void snapshotPreviousHour() {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        snapshotScheduled(to.minus(1, ChronoUnit.HOURS), to);
    }

    /**
     * 주어진 창의 모델버전별 SCHEDULED 스냅샷. 창을 파라미터로 받아 결정적으로 테스트 가능
     * (스케줄 메서드는 now 기준 직전 1시간으로 이걸 호출). 이미 있는 (기간,버전) 런은 건너뛴다.
     *
     * 참고: getMetrics의 missedBreaches는 창 전역(모델버전 무필터) — "그 창에 활성 예측 없이 난
     * 이탈"이라 어느 모델이 놓쳤는지 per-breach로 특정할 수 없다. 정상 운영에선 창마다 한 모델만
     * 돌아(모델은 프로세스 env 토글) 그 값이 그 모델 몫이고, v1 vs v2 비교(PR5)도 분리된 페이즈
     * 창을 써서 정확하다. 한 창에 두 버전이 공존(런 중 모델 교체)하면 두 행이 같은 전역값을 갖는다.
     */
    public void snapshotScheduled(Instant from, Instant to) {
        for (String version : predictionRepository.findDistinctModelVersions(from, to)) {
            if (evaluationRunRepository.existsByPeriodStartAndPeriodEndAndModelVersionAndTriggerType(
                    from, to, version, TriggerType.SCHEDULED)) {
                continue;
            }
            PredictionMetricsResponse metrics = predictionMetricsService.getMetrics(from, to, version);
            try {
                evaluationRunRepository.save(EvaluationRun.snapshot(null, TriggerType.SCHEDULED, metrics));
            } catch (DataIntegrityViolationException e) {
                // 부분 유니크 인덱스 backstop — existsBy와 save 사이 경쟁(다중 인스턴스, M6 수평확장)으로
                // 같은 (기간,버전) 런이 이미 들어왔다. 이 버전만 건너뛰고 나머지 버전은 계속 스냅샷한다.
                log.debug("평가 런 중복(동시 발화) 건너뜀: {} [{}~{}]", version, from, to);
                continue;
            }
            log.info("평가 런 스냅샷: {} [{}~{}] TP={} FP={} missed={}",
                    version, from, to, metrics.truePositives(), metrics.falsePositives(), metrics.missedBreaches());
        }
    }
}
