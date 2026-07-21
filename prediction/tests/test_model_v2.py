"""v2-newton 모델 테스트. 뉴턴 냉각 물리로 생성한 해석해 곡선으로 정확도를 고정하고,
v1 대비 구조적 개선(점근 가드)·급변 보정(regime-trim)·폴백을 검증한다."""
import math
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import pytest

from app import model_v2
from app.model import MIN_WINDOW_SIZE, ReadingPoint

BASE = datetime(2026, 1, 1, tzinfo=timezone.utc)


@dataclass
class Ctx:
    ambientTemp: float | None = None
    remainingDistanceMeters: float | None = None


def newton_window(ambient: float, t0: float, k: float, n: int, interval_min: float = 1.0,
                  ambient_temp: float | None = None):
    """T(t) = ambient + (t0 - ambient)·e^(-k·t), 노이즈 없는 해석해 곡선. ambient_temp 미지정 시 ambient."""
    pts = []
    for i in range(n):
        t = i * interval_min
        temp = ambient + (t0 - ambient) * math.exp(-k * t)
        pts.append(ReadingPoint(ts=BASE + timedelta(minutes=t), temperature=round(temp, 4),
                                ambient_temp=ambient if ambient_temp is None else ambient_temp))
    return pts


def test_newton_curve_predicts_breach_near_analytic_time():
    # ambient 25, 초기 5, k=0.03/분 — 윈도우(6점)는 전부 임계 아래, 이탈은 미래(≈5.4분).
    # 예측 시각이 해석해 t*와 ±1분 이내.
    ambient, t0, k, threshold = 25.0, 5.0, 0.03, 8.0
    w = newton_window(ambient, t0, k, n=6)  # t=0..5, latest 7.79 < 8
    result = model_v2.predict(w, threshold_temp=threshold, context=Ctx(ambientTemp=ambient))

    assert result.will_breach is True
    assert result.model_version == "v2-newton"
    assert result.confidence is None
    t_star = (1.0 / k) * math.log((ambient - t0) / (ambient - threshold))
    expected_at = BASE + timedelta(minutes=t_star)
    assert abs((result.predicted_breach_at - expected_at).total_seconds()) <= 60


def test_asymptotic_guard_ambient_below_threshold_no_breach():
    # ambient 7 ≤ threshold 8 — 물리적으로 도달 불가. v2는 no_breach.
    w = newton_window(ambient=7.0, t0=5.0, k=0.05, n=10)
    result = model_v2.predict(w, threshold_temp=8.0, context=Ctx(ambientTemp=7.0))
    assert result.will_breach is False
    assert result.model_version == "v2-newton"


def test_v1_would_false_positive_where_v2_holds():
    # plateau형: ambient 7로 점근하는 상승 곡선. v1(선형)은 상승 기울기로 이탈 예측(FP),
    # v2는 점근 가드로 no_breach — 대비를 한 윈도우로 증명.
    from app import model as v1
    w = newton_window(ambient=7.0, t0=4.0, k=0.05, n=8)
    v1_result = v1.predict(w, threshold_temp=8.0)
    v2_result = model_v2.predict(w, threshold_temp=8.0, context=Ctx(ambientTemp=7.0))

    assert v1_result.will_breach is True   # 선형 외삽은 임계 도달로 오판
    assert v2_result.will_breach is False  # 물리는 도달 불가로 정판


def test_regime_trim_uses_only_new_regime_after_shift():
    # 냉동기 고장 재현: 앞 5점 ambient 4(정상), 뒤 5점 ambient 25(고장) + 급상승.
    # 최신 regime(ambient 25) 5점으로 재적합해 이탈 예측 — 옛 정상점에 오염되지 않는다.
    old = newton_window(ambient=4.0, t0=4.0, k=0.05, n=5, ambient_temp=4.0)
    # 뒤 regime: 5.0에서 시작해 ambient 25로 상승(k=0.03이라 5점 전부 임계 아래), 앞 구간 뒤에 이어붙임
    new = []
    for i in range(5):
        t = 5 + i
        temp = 25.0 + (5.0 - 25.0) * math.exp(-0.03 * i)  # i=4: 7.26 < 8
        new.append(ReadingPoint(ts=BASE + timedelta(minutes=t), temperature=round(temp, 4), ambient_temp=25.0))
    result = model_v2.predict(old + new, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    assert result.will_breach is True
    assert result.model_version == "v2-newton"


def test_regime_trim_silent_until_new_regime_has_min_window():
    # 급변 직후 새 regime 점이 4개뿐(MIN_WINDOW_SIZE 미만) → 침묵(no_breach). 2~3점 재적합 금지.
    old = newton_window(ambient=4.0, t0=4.0, k=0.05, n=6, ambient_temp=4.0)
    new = [ReadingPoint(ts=BASE + timedelta(minutes=6 + i), temperature=6.0 + i, ambient_temp=25.0)
           for i in range(4)]
    result = model_v2.predict(old + new, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    assert result.will_breach is False


def test_fallback_to_v1_when_ambient_absent():
    # ambient 전무(context·per-point 모두 None) → v1 경로 실행, modelVersion을 정직하게 v1-linear로.
    w = [ReadingPoint(ts=BASE + timedelta(minutes=i), temperature=4.0 + i) for i in range(5)]
    result = model_v2.predict(w, threshold_temp=9.0, context=Ctx())
    assert result.model_version == "v1-linear"
    assert result.will_breach is True  # 선형 상승 → v1이 이탈 예측


def test_arrival_imminent_suppresses_prediction():
    # 목적지 150m — 곧 배송 완료라 예측 억제(이탈 곡선이어도 no_breach).
    w = newton_window(ambient=25.0, t0=5.0, k=0.05, n=10)
    result = model_v2.predict(w, threshold_temp=8.0,
                              context=Ctx(ambientTemp=25.0, remainingDistanceMeters=150.0))
    assert result.will_breach is False


def test_below_min_window_no_breach():
    w = newton_window(ambient=25.0, t0=5.0, k=0.05, n=MIN_WINDOW_SIZE - 1)
    result = model_v2.predict(w, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    assert result.will_breach is False


def test_already_breached_no_breach():
    # 최신 온도가 이미 임계 초과 — L2 영역, v2도 no_breach.
    w = newton_window(ambient=25.0, t0=9.0, k=0.05, n=8)  # 9도에서 시작, 임계 8 이미 초과
    result = model_v2.predict(w, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    assert result.will_breach is False


def test_none_ambient_old_points_excluded_from_fit():
    # 전환 구간(M7 배포 경계): 앞 4점 ambient=None(구 데이터), 뒤 6점 ambient=25(신 데이터).
    # None 점을 regime에서 버리므로 fit이 오염되지 않는다 — 신 regime 6점만으로 판정.
    old_none = [ReadingPoint(ts=BASE + timedelta(minutes=i), temperature=2.0) for i in range(4)]
    new = newton_window(ambient=25.0, t0=5.0, k=0.03, n=6)
    for i, p in enumerate(new):
        p.ts = BASE + timedelta(minutes=4 + i)  # 옛 구간 뒤로 이어붙임
    result = model_v2.predict(old_none + new, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    # 오염 없이 신 regime(상승, ambient 25)만 봐서 이탈 예측
    assert result.will_breach is True
    assert result.model_version == "v2-newton"


def test_none_ambient_below_min_window_stays_silent():
    # 신 regime(ambient 실린) 점이 4개뿐 → None 옛 점을 버리면 MIN_WINDOW 미달 → 침묵.
    old_none = [ReadingPoint(ts=BASE + timedelta(minutes=i), temperature=2.0) for i in range(6)]
    new = [ReadingPoint(ts=BASE + timedelta(minutes=6 + i), temperature=6.0 + i * 0.3, ambient_temp=25.0)
           for i in range(4)]
    result = model_v2.predict(old_none + new, threshold_temp=8.0, context=Ctx(ambientTemp=25.0))
    assert result.will_breach is False
