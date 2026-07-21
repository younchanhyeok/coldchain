"""L3 예측 v2 — 뉴턴 냉각 물리 모델.

warming 구간에서 ln(ambient − T)가 시각에 선형(T(t)=ambient+(T0−ambient)·e^(−k·t))임을 이용해
냉각계수 k를 최소제곱으로 추정하고, 임계 도달 시각을 물리식으로 외삽한다. v1(온도-시간 선형)
대비 두 구조적 개선:
  (1) ambient ≤ threshold면 물리적으로 도달 불가 → v1의 구조적 오탐(plateau)을 제거.
  (2) 급변으로 ambient가 바뀌면(냉동기 고장) 옛 regime 점을 버리고 새 조건으로 빠르게 재적합.

confidence는 v1과 마찬가지로 항상 None — 물리 외삽도 확률을 산출하지 않는다(과장 금지 원칙).
ambient가 없으면(외기 센서 미탑재 디바이스·구 데이터) v1으로 폴백하고 modelVersion을
"v1-linear"로 정직하게 보고한다 — 실제로 돈 코드가 v1이므로 지표가 오염되지 않는다.
"""
from datetime import timedelta

import numpy as np

from app import model as v1
from app.model import MAX_HORIZON_MINUTES, MIN_WINDOW_SIZE, PredictionResult, ReadingPoint

MODEL_VERSION = "v2-newton"

REGIME_EPS = 1.0        # 최신 ambient와 이만큼(℃) 차이나는 옛 점은 다른 regime → 버림
AMBIENT_MARGIN = 0.1    # ambient가 threshold+이 값(℃) 이하면 도달 불가로 판정(점근 가드)
MIN_GAP = 0.3           # ambient−T가 이보다 작으면(℃) log 발산 위험 → 그 점 제외
ARRIVAL_METERS = 200.0  # 목적지 이 거리(m) 안이면 예측 억제(곧 배송 완료라 무의미)


def predict(window: list[ReadingPoint], threshold_temp: float, context=None) -> PredictionResult:
    if len(window) < MIN_WINDOW_SIZE:
        return _no_breach()

    ambient = _current_ambient(window, context)
    if ambient is None:
        # 외기온 없음 → 물리 모델 불가. v1으로 폴백(modelVersion은 v1-linear로 정직하게).
        return v1.predict(window, threshold_temp, context=context)

    # regime-trim: 최신 ambient와 같은 regime의 점만 남긴다. ambient가 없는 점(M7 배포 전 구
    # 데이터·간헐 센서 결손)은 regime을 확인할 수 없으므로 **버린다** — 남겨서 fit에 넣으면 옛
    # regime의 온도를 현재 ambient로 잘못 환산해 k·t0를 오염시킨다(전환 구간엔 ambient 실린 5점이
    # 모일 때까지 침묵하는 게 정직하다).
    ordered = sorted(window, key=lambda p: p.ts)
    regime = [p for p in ordered
              if p.ambient_temp is not None and abs(p.ambient_temp - ambient) <= REGIME_EPS]
    if len(regime) < MIN_WINDOW_SIZE:
        return _no_breach()  # 새/유효 regime 표본 부족 — 급변 직후·전환 구간엔 5점 모일 때까지 침묵

    latest = regime[-1]

    remaining = getattr(context, "remainingDistanceMeters", None) if context is not None else None
    if remaining is not None and remaining < ARRIVAL_METERS:
        return _no_breach()  # 도착 임박 — 예측이 트래커 송신 중단으로 EXPIRED(오탐) 되는 꼬리 제거

    if latest.temperature >= threshold_temp:
        return _no_breach()  # 이미 이탈 — L2/FR-6의 영역

    if ambient <= threshold_temp + AMBIENT_MARGIN:
        return _no_breach()  # ★ 물리적으로 임계 도달 불가 — v1의 구조적 오탐을 여기서 제거

    # ln(ambient − T) vs 시각(분) 선형 적합. ambient−T가 너무 작은 점은 log 발산 위험이라 제외.
    t0 = regime[0].ts
    xs, ys = [], []
    for p in regime:
        gap = ambient - p.temperature
        if gap >= MIN_GAP:
            xs.append((p.ts - t0).total_seconds() / 60.0)
            ys.append(np.log(gap))
    if len(xs) < MIN_WINDOW_SIZE:
        return _no_breach()

    slope, intercept = np.polyfit(np.array(xs), np.array(ys), 1)  # y = slope·x + intercept, slope = −k
    k = -slope
    if k <= 0:
        return _no_breach()  # 냉각·평탄 — 이탈로 향하지 않음

    # 임계 도달 절대시각(분): ln(ambient−threshold) = intercept − k·t*  →  t* = (intercept − ln(gap_th)) / k
    t_star = (intercept - np.log(ambient - threshold_temp)) / k
    latest_minutes = (latest.ts - t0).total_seconds() / 60.0
    minutes_to_breach = t_star - latest_minutes

    if minutes_to_breach <= 0 or minutes_to_breach > MAX_HORIZON_MINUTES:
        return _no_breach()  # 이미 지났거나(적합상) 너무 먼 미래 — 실행 가능한 경고 아님

    # 최신 시점 순간기울기 dT/dt = k·(ambient − T) (뉴턴 냉각) — 계약의 slopePerMinute·프론트 근거리 정합용.
    instantaneous_slope = k * (ambient - latest.temperature)
    return PredictionResult(
        will_breach=True,
        predicted_breach_at=latest.ts + timedelta(minutes=float(minutes_to_breach)),
        slope_per_minute=float(instantaneous_slope),
        confidence=None,
        model_version=MODEL_VERSION,
    )


def _current_ambient(window: list[ReadingPoint], context):
    """현재 regime의 외기온 — 최신 리딩의 per-point ambient를 우선한다(그 점에 물리적으로 붙어
    있는 값이라 regime 판정과 절대 어긋나지 않는다). 없으면 context 스칼라로 폴백. 둘 다 없으면
    None → 호출부가 v1으로 폴백. (역순으로 context를 우선하면 stale한 context가 최신 regime을
    통째로 trim시켜 이탈을 침묵시킬 수 있어 per-point를 신뢰한다.)"""
    latest = max(window, key=lambda p: p.ts)
    if latest.ambient_temp is not None:
        return latest.ambient_temp
    if context is not None:
        return getattr(context, "ambientTemp", None)
    return None


def _no_breach() -> PredictionResult:
    return PredictionResult(
        will_breach=False,
        predicted_breach_at=None,
        slope_per_minute=0.0,
        confidence=None,
        model_version=MODEL_VERSION,
    )
