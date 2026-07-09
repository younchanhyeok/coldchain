from datetime import datetime

from pydantic import BaseModel


class WindowPoint(BaseModel):
    ts: datetime
    temperature: float


class PredictContext(BaseModel):
    """v2 다변량용 자리 — MVP는 항상 null. 계약에 처음부터 포함해 M7 확장 시 인터페이스가 안 바뀐다."""

    ambientTemp: float | None = None
    remainingDistanceMeters: float | None = None


class PredictRequest(BaseModel):
    trackerId: str
    thresholdTemp: float
    window: list[WindowPoint]
    context: PredictContext | None = None


class PredictResponse(BaseModel):
    willBreach: bool
    predictedBreachAt: datetime | None
    confidence: float | None
    slopePerMinute: float
    modelVersion: str
