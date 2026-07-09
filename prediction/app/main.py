from fastapi import FastAPI

from app.model import ReadingPoint, predict
from app.schemas import PredictRequest, PredictResponse

app = FastAPI(title="coldchain-prediction")


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.post("/internal/v1/predict", response_model=PredictResponse)
def predict_endpoint(request: PredictRequest) -> PredictResponse:
    window = [ReadingPoint(ts=p.ts, temperature=p.temperature) for p in request.window]
    result = predict(window, request.thresholdTemp)
    return PredictResponse(
        willBreach=result.will_breach,
        predictedBreachAt=result.predicted_breach_at,
        confidence=result.confidence,
        slopePerMinute=result.slope_per_minute,
        modelVersion=result.model_version,
    )
