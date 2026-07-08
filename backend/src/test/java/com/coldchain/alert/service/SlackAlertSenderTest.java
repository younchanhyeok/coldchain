package com.coldchain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SlackAlertSenderTest {

    @Test
    void sendFailsImmediatelyWithoutHttpCallWhenWebhookNotConfigured() {
        SlackAlertSender sender = new SlackAlertSender(RestClient.builder(), "");

        SlackAlertSender.SendResult result = sender.send("테스트 메시지");

        assertThat(result.success()).isFalse();
        assertThat(result.attempts()).isEqualTo(0);
        assertThat(result.retryCount()).isEqualTo(0);
    }

    @Test
    void sendResultRetryCountIsAttemptsMinusOneClampedAtZero() {
        assertThat(SlackAlertSender.SendResult.success(1).retryCount()).isEqualTo(0);
        assertThat(SlackAlertSender.SendResult.success(3).retryCount()).isEqualTo(2);
        assertThat(SlackAlertSender.SendResult.failed(3).retryCount()).isEqualTo(2);
        assertThat(SlackAlertSender.SendResult.failed(0).retryCount()).isEqualTo(0);
    }
}
