"""L3 예측 순수 로직 — FastAPI/pydantic을 모른다(단위 테스트가 웹 프레임워크 없이 돈다).

v1: 최근 온도 윈도우에 선형회귀를 적합해 기울기(°C/분)를 구하고, 그 기울기로 임계
도달 시각을 외삽한다. confidence는 항상 None — 선형회귀는 확률을 산출하지 않으므로
신뢰도를 창작하지 않는다(콜드체인 프로젝트 "과장 금지" 원칙).
"""

from dataclasses import dataclass
from datetime import datetime, timedelta

import numpy as np
from sklearn.linear_model import LinearRegression

MODEL_VERSION = "v1-linear"
MAX_HORIZON_MINUTES = 120.0
# L2(AnomalyDetectionService.MIN_WINDOW_SIZE)와 동일한 값 — 표본 2~3개의 선형회귀는
# 노이즈 평활이 사실상 0이라(뉴턴 냉각 시뮬레이터 노이즈 ±0.15℃), 리딩 한 쌍의 우연한
# 기울기가 그대로 허위 예측(오탐률 저하)으로 이어진다.
MIN_WINDOW_SIZE = 5


@dataclass
class ReadingPoint:
    ts: datetime
    temperature: float
    ambient_temp: float | None = None  # M7 v2용 — v1은 무시


@dataclass
class PredictionResult:
    will_breach: bool
    predicted_breach_at: datetime | None
    slope_per_minute: float
    confidence: float | None
    model_version: str


def predict(window: list[ReadingPoint], threshold_temp: float, context=None) -> PredictionResult:
    # context(외기온·잔여거리)는 v1이 무시한다 — 시그니처만 M7에서 통일해 디스패처(main)가
    # v1/v2를 같은 호출 규약으로 부를 수 있게 한다. v2-newton은 context를 실제로 사용.
    if len(window) < MIN_WINDOW_SIZE:
        return _no_breach(0.0)

    ordered = sorted(window, key=lambda p: p.ts)
    t0 = ordered[0].ts
    minutes = np.array([[(p.ts - t0).total_seconds() / 60.0] for p in ordered])
    temperatures = np.array([p.temperature for p in ordered])

    model = LinearRegression().fit(minutes, temperatures)
    slope = float(model.coef_[0])

    latest = ordered[-1]

    # 하강·평탄 추세는 이탈로 향하지 않는다 — 예측할 미래 이탈이 없음.
    if slope <= 0:
        return _no_breach(slope)

    # 이미 임계를 넘은 상태는 L3(사전 예측)의 영역이 아니다 — L2/FR-6이 이미 확정 이탈로 처리.
    if latest.temperature >= threshold_temp:
        return _no_breach(slope)

    minutes_to_breach = (threshold_temp - latest.temperature) / slope

    # 너무 먼 미래(2시간 초과)는 실행 가능한 경고가 아니다 — 추세가 그 사이 얼마든지 바뀔 수 있다.
    if minutes_to_breach > MAX_HORIZON_MINUTES:
        return _no_breach(slope)

    predicted_breach_at = latest.ts + timedelta(minutes=minutes_to_breach)
    return PredictionResult(
        will_breach=True,
        predicted_breach_at=predicted_breach_at,
        slope_per_minute=slope,
        confidence=None,
        model_version=MODEL_VERSION,
    )


def _no_breach(slope: float) -> PredictionResult:
    return PredictionResult(
        will_breach=False,
        predicted_breach_at=None,
        slope_per_minute=slope,
        confidence=None,
        model_version=MODEL_VERSION,
    )
