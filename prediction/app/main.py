import logging
import os

from fastapi import FastAPI

from app import model as model_v1
from app import model_v2
from app.model import ReadingPoint
from app.schemas import PredictRequest, PredictResponse

logger = logging.getLogger(__name__)

# 모델 토글(M7) — 기동 환경변수로 v1/v2 선택. 컨테이너를 env 바꿔 재기동하는 방식이라(A/B 비교)
# 모듈 로드 시점에 한 번 결정한다. 응답의 modelVersion은 각 모델이 정하므로(폴백 시 v1-linear)
# 지표 파이프라인이 자동으로 분리 집계한다. 미지 값은 v1로 안전 폴백.
_PREDICT_FNS = {"v1": model_v1.predict, "v2": model_v2.predict}
_MODEL_NAME = os.getenv("PREDICTION_MODEL", "v1").lower()
_predict = _PREDICT_FNS.get(_MODEL_NAME, model_v1.predict)
logger.info("prediction model = %s (PREDICTION_MODEL=%s)", _predict.__module__, _MODEL_NAME)

app = FastAPI(title="coldchain-prediction")


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


# /internal 접두사 = 공개 노출 금지, Spring에서만 호출하는 내부 전용 계약(docker-compose
# 네트워크 경계에 의존 — 별도 인증 없음, 외부에 직접 노출되면 안 됨).
@app.post("/internal/v1/predict", response_model=PredictResponse)
def predict_endpoint(request: PredictRequest) -> PredictResponse:
    # ambientTemp를 per-point로 전달. 활성 모델(_predict)이 v1이면 context/ambient를 무시.
    window = [ReadingPoint(ts=p.ts, temperature=p.temperature, ambient_temp=p.ambientTemp) for p in request.window]
    result = _predict(window, request.thresholdTemp, context=request.context)
    return PredictResponse(
        willBreach=result.will_breach,
        predictedBreachAt=result.predicted_breach_at,
        confidence=result.confidence,
        slopePerMinute=result.slope_per_minute,
        modelVersion=result.model_version,
    )
