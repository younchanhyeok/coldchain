package com.coldchain.prediction.service;

import com.coldchain.ingest.event.ReadingRecordedEvent;
import com.coldchain.prediction.domain.BreachEvent;
import com.coldchain.prediction.domain.Prediction;
import com.coldchain.prediction.domain.PredictionStatus;
import com.coldchain.prediction.event.PredictionChangedEvent;
import com.coldchain.prediction.repository.BreachEventRepository;
import com.coldchain.prediction.repository.PredictionRepository;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.repository.ReadingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * L3 예측 라이프사이클 — 생성(ACTIVE)·갱신·취소(CANCELED)·무효화(INVALIDATED)·만료(EXPIRED)·
 * 적중(BREACHED). 트래커별 락으로 같은 트래커 이벤트를 직렬화한 뒤 락 "안에서" 트랜잭션을 연다
 * (@Transactional이면 프록시가 락보다 먼저 트랜잭션=커넥션을 잡아 대기 스레드가 커넥션을 쥔 채
 * 블록될 수 있다 — M3 사전검토에서 확립한 패턴). {@link PredictionChangedEvent}는 트랜잭션
 * 커밋 후에만 소비되도록(AFTER_COMMIT) 구독 측이 설계돼 있어, 커밋 실패 시 유령 알림이 없다.
 */
@Service
public class PredictionService {

    // Python model.py의 MIN_WINDOW_SIZE와 동일 — 판정 자체가 스킵될 걸 알면서 불필요한 HTTP
    // 호출을 만들지 않는다.
    static final int MIN_WINDOW_SIZE = 5;
    static final int CALM_STREAK_THRESHOLD = 3;
    static final long EXPIRE_GRACE_MINUTES = 15;

    private final PredictionRepository predictionRepository;
    private final BreachEventRepository breachEventRepository;
    private final ReadingRepository readingRepository;
    private final PredictionClient predictionClient;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<String, Object> trackerLocks = new ConcurrentHashMap<>();

    public PredictionService(PredictionRepository predictionRepository, BreachEventRepository breachEventRepository,
            ReadingRepository readingRepository, PredictionClient predictionClient,
            ApplicationEventPublisher eventPublisher, TransactionTemplate transactionTemplate) {
        this.predictionRepository = predictionRepository;
        this.breachEventRepository = breachEventRepository;
        this.readingRepository = readingRepository;
        this.predictionClient = predictionClient;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    public void analyze(ReadingRecordedEvent event) {
        Object lock = trackerLocks.computeIfAbsent(event.trackerId(), id -> new Object());
        synchronized (lock) {
            transactionTemplate.executeWithoutResult(status -> analyzeLocked(event));
        }
    }

    /** L2 SUDDEN 활성화 시 호출 — 급변으로 선형 추세 가정이 깨졌으므로 예측을 신뢰할 수 없다. */
    public void invalidate(String trackerId, Instant ts) {
        Object lock = trackerLocks.computeIfAbsent(trackerId, id -> new Object());
        synchronized (lock) {
            transactionTemplate.executeWithoutResult(status -> invalidateLocked(trackerId));
        }
    }

    /** 예상 이탈 시각을 넘기고도 여전히 ACTIVE인(=이탈 없이 시간만 지난) 예측을 정리한다. */
    @Scheduled(fixedRate = 60_000)
    public void expireStalePredictions() {
        Instant cutoff = Instant.now().minus(EXPIRE_GRACE_MINUTES, ChronoUnit.MINUTES);
        List<Prediction> stale = predictionRepository.findByStatusAndPredictedBreachAtBefore(
                PredictionStatus.ACTIVE, cutoff);

        for (Prediction prediction : stale) {
            String trackerId = prediction.getTrackerId();
            Long id = prediction.getId();
            Object lock = trackerLocks.computeIfAbsent(trackerId, key -> new Object());
            synchronized (lock) {
                transactionTemplate.executeWithoutResult(status -> expireLocked(id));
            }
        }
    }

    private void analyzeLocked(ReadingRecordedEvent event) {
        String trackerId = event.trackerId();
        boolean isBreached = event.temperature().compareTo(event.thresholdTemp()) > 0;

        if (isBreached) {
            if (event.justBreached()) {
                breachEventRepository.save(new BreachEvent(trackerId, event.recordedAt()));
                predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE)
                        .ifPresent(prediction -> {
                            prediction.markBreached(event.recordedAt());
                            publishChanged(prediction);
                        });
            }
            return; // 확정 이탈 동안은 L3가 관여하지 않는다 — L2/FR-6의 영역
        }

        Optional<Prediction> active = predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE);

        List<Reading> recent = readingRepository.findTop30ByTrackerIdOrderByRecordedAtDesc(trackerId);
        if (recent.size() < MIN_WINDOW_SIZE) {
            return; // 콜드 스타트 가드 — L2와 동일한 원칙
        }

        List<PredictionClient.WindowPoint> window = recent.stream()
                .sorted(Comparator.comparing(Reading::getRecordedAt))
                .map(r -> new PredictionClient.WindowPoint(r.getRecordedAt(), r.getTemperature()))
                .toList();

        Optional<PredictionClient.Result> resultOpt = predictionClient.predict(trackerId, event.thresholdTemp(), window);
        if (resultOpt.isEmpty()) {
            return; // 예측 서버 장애·쿨다운 — 이번 리딩은 스킵(NFR-3: 수집·탐지엔 영향 없음)
        }
        PredictionClient.Result result = resultOpt.get();

        if (!result.willBreach()) {
            active.ifPresent(prediction -> {
                prediction.recordCalmReading();
                if (prediction.shouldCancel(CALM_STREAK_THRESHOLD)) {
                    prediction.cancel();
                    publishChanged(prediction);
                }
            });
            return;
        }

        if (active.isPresent()) {
            Prediction prediction = active.get();
            prediction.refresh(event.recordedAt(), result.predictedBreachAt(), result.slopePerMinute(),
                    event.temperature());
            // 갱신만으로는 이벤트를 발행하지 않는다 — 매 리딩마다 재알림하면 도배된다(anomaly와 동일 원칙).
        } else {
            Prediction created = Prediction.activate(trackerId, event.recordedAt(), event.thresholdTemp(),
                    result.predictedBreachAt(), result.slopePerMinute(), result.modelVersion(), event.temperature());
            predictionRepository.save(created);
            publishChanged(created);
        }
    }

    private void invalidateLocked(String trackerId) {
        predictionRepository.findByTrackerIdAndStatus(trackerId, PredictionStatus.ACTIVE).ifPresent(prediction -> {
            prediction.invalidate();
            publishChanged(prediction);
        });
    }

    private void expireLocked(Long predictionId) {
        predictionRepository.findById(predictionId)
                // analyze()가 그새 다른 상태로 이미 바꿨을 수 있다(취소/적중/무효화) — 재확인 후에만 만료.
                .filter(prediction -> prediction.getStatus() == PredictionStatus.ACTIVE)
                .ifPresent(prediction -> {
                    prediction.expire();
                    publishChanged(prediction);
                });
    }

    private void publishChanged(Prediction prediction) {
        eventPublisher.publishEvent(new PredictionChangedEvent(
                prediction.getId(), prediction.getTrackerId(), prediction.getStatus(),
                prediction.getPredictedBreachAt(), prediction.getSlopePerMinute(), prediction.getModelVersion(),
                prediction.getCreatedAt()));
    }
}
