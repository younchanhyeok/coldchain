package com.coldchain.prediction.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Python 예측 서버(내부 전용, /internal/v1/predict) 호출. 타임아웃 2s·재시도 1회 후에도
 * 실패하면 짧은 쿨다운(서킷 오픈) 동안 호출 자체를 생략한다 — Python이 죽었을 때 매 리딩마다
 * 2s+재시도 타임아웃을 반복해 @Async 스레드 풀을 잠식하는 것을 막는다(NFR-3: 예측이 죽어도
 * 수집·탐지는 무중단).
 */
@Component
public class PredictionClient {

    private static final Logger log = LoggerFactory.getLogger(PredictionClient.class);
    private static final int MAX_ATTEMPTS = 2; // 최초 1회 + 재시도 1회
    private static final int TIMEOUT_MILLIS = 2_000;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String baseUrl;
    private volatile Instant circuitOpenUntil = Instant.EPOCH;

    public PredictionClient(RestClient.Builder restClientBuilder,
            @Value("${app.prediction.base-url:http://localhost:8000}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.baseUrl = baseUrl;
    }

    public record WindowPoint(Instant ts, BigDecimal temperature) {
    }

    public record Result(boolean willBreach, Instant predictedBreachAt, BigDecimal slopePerMinute, String modelVersion) {
    }

    /** 예측 서버 장애·쿨다운 중이면 빈 Optional — 호출부는 이번 리딩을 조용히 스킵한다. */
    public Optional<Result> predict(String trackerId, BigDecimal thresholdTemp, List<WindowPoint> window) {
        if (Instant.now().isBefore(circuitOpenUntil)) {
            return Optional.empty();
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PredictApiResponse body = restClient.post()
                        .uri(baseUrl + "/internal/v1/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new PredictApiRequest(trackerId, thresholdTemp, toApiWindow(window), null))
                        .retrieve()
                        .body(PredictApiResponse.class);
                circuitOpenUntil = Instant.EPOCH; // 성공 시 서킷 리셋
                return Optional.ofNullable(body).map(this::toResult);
            } catch (Exception e) {
                log.warn("예측 서버 호출 실패(시도 {}/{}, trackerId={}): {}", attempt, MAX_ATTEMPTS, trackerId, e.toString());
            }
        }
        circuitOpenUntil = Instant.now().plus(COOLDOWN);
        return Optional.empty();
    }

    private Result toResult(PredictApiResponse body) {
        return new Result(body.willBreach(), body.predictedBreachAt(), body.slopePerMinute(), body.modelVersion());
    }

    private List<PredictApiRequest.WindowPointBody> toApiWindow(List<WindowPoint> window) {
        return window.stream().map(w -> new PredictApiRequest.WindowPointBody(w.ts(), w.temperature())).toList();
    }

    private record PredictApiRequest(
            String trackerId, BigDecimal thresholdTemp, List<WindowPointBody> window, Object context) {
        private record WindowPointBody(Instant ts, BigDecimal temperature) {
        }
    }

    private record PredictApiResponse(
            boolean willBreach, Instant predictedBreachAt, BigDecimal confidence, BigDecimal slopePerMinute,
            String modelVersion) {
    }
}
