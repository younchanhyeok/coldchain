package com.coldchain.alert.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Slack 인커밍 웹훅으로 발송한다. 최대 3회 시도(최초 1회 + 재시도 2회) 후 실패로 처리 —
 * 대체 채널은 없다(Slack이 유일한 실채널, SMS/카카오톡은 미구현).
 */
@Component
public class SlackAlertSender {

    private static final Logger log = LoggerFactory.getLogger(SlackAlertSender.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackAlertSender(RestClient.Builder restClientBuilder,
            @Value("${app.alert.slack.webhook-url:}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    public SendResult send(String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack 웹훅이 설정되지 않아 발송을 건너뜀: {}", message);
            return SendResult.failed(0);
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri(webhookUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("text", message))
                        .retrieve()
                        .toBodilessEntity();
                return SendResult.success(attempt);
            } catch (Exception e) {
                log.warn("Slack 발송 실패(시도 {}/{}): {}", attempt, MAX_ATTEMPTS, e.toString());
            }
        }
        return SendResult.failed(MAX_ATTEMPTS);
    }

    public record SendResult(boolean success, int attempts) {
        public static SendResult success(int attempts) {
            return new SendResult(true, attempts);
        }

        public static SendResult failed(int attempts) {
            return new SendResult(false, attempts);
        }

        public int retryCount() {
            return Math.max(0, attempts - 1);
        }
    }
}
