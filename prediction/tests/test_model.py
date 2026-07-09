from datetime import datetime, timedelta, timezone

import pytest

from app.model import MAX_HORIZON_MINUTES, MODEL_VERSION, ReadingPoint, predict

BASE = datetime(2026, 1, 1, tzinfo=timezone.utc)


def window(temps: list[float], interval_minutes: float = 1.0) -> list[ReadingPoint]:
    return [ReadingPoint(ts=BASE + timedelta(minutes=i * interval_minutes), temperature=t)
            for i, t in enumerate(temps)]


def test_rising_trend_within_horizon_predicts_breach():
    # 0분부터 1분마다 1도씩 상승, 5개 리딩(MIN_WINDOW_SIZE) — 8도 임계까지 4분 남음
    w = window([4.0, 5.0, 6.0, 7.0, 8.0])
    result = predict(w, threshold_temp=9.0)

    assert result.will_breach is True
    assert result.predicted_breach_at is not None
    assert result.slope_per_minute == pytest.approx(1.0, abs=0.01)
    assert result.confidence is None  # v1은 신뢰도를 산출하지 않는다 — 창작 금지
    assert result.model_version == MODEL_VERSION


def test_flat_trend_does_not_predict_breach():
    w = window([4.0, 4.0, 4.0, 4.0, 4.0])
    result = predict(w, threshold_temp=8.0)

    assert result.will_breach is False
    assert result.predicted_breach_at is None


def test_falling_trend_does_not_predict_breach():
    w = window([10.0, 8.0, 6.0, 4.0, 2.0])
    result = predict(w, threshold_temp=8.0)

    assert result.will_breach is False


def test_already_over_threshold_is_not_l3_job():
    # L2/FR-6이 이미 확정 이탈로 처리하는 영역 — L3는 "아직 안 넘었지만 넘을 것"만 예측한다
    w = window([7.0, 8.0, 9.0, 9.5, 10.0])
    result = predict(w, threshold_temp=8.0)

    assert result.will_breach is False


def test_slow_rise_beyond_horizon_does_not_predict_breach():
    # 매우 완만한 상승 — 임계 도달까지 120분을 훨씬 초과
    w = window([4.0, 4.01, 4.02, 4.03, 4.04], interval_minutes=10.0)
    result = predict(w, threshold_temp=8.0)

    assert result.will_breach is False
    assert result.slope_per_minute > 0  # 상승 추세 자체는 맞지만 지평선 밖


def test_insufficient_window_does_not_predict_breach():
    # MIN_WINDOW_SIZE(5) 미만 — 표본이 적으면 노이즈 한 쌍의 우연한 기울기로 오탐이 나므로
    # L2의 콜드 스타트 가드와 동일하게 판정 자체를 스킵한다.
    w = window([4.0, 8.0, 5.0, 9.0])
    result = predict(w, threshold_temp=8.0)

    assert result.will_breach is False
    assert result.slope_per_minute == 0.0


def test_empty_window_does_not_crash():
    result = predict([], threshold_temp=8.0)

    assert result.will_breach is False


def test_predicted_breach_at_is_within_horizon():
    w = window([4.0, 5.0, 6.0, 7.0, 8.0])
    result = predict(w, threshold_temp=9.0)

    assert result.predicted_breach_at <= w[-1].ts + timedelta(minutes=MAX_HORIZON_MINUTES)
