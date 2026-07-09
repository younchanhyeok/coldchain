from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_healthz():
    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_predict_endpoint_rising_window():
    response = client.post(
        "/internal/v1/predict",
        json={
            "trackerId": "TRK-0001",
            "thresholdTemp": 8.0,
            "window": [
                {"ts": "2026-01-01T00:00:00Z", "temperature": 4.0},
                {"ts": "2026-01-01T00:01:00Z", "temperature": 5.0},
                {"ts": "2026-01-01T00:02:00Z", "temperature": 6.0},
                {"ts": "2026-01-01T00:03:00Z", "temperature": 7.0},
            ],
            "context": None,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["willBreach"] is True
    assert body["predictedBreachAt"] is not None
    assert body["confidence"] is None
    assert body["modelVersion"] == "v1-linear"


def test_predict_endpoint_flat_window():
    response = client.post(
        "/internal/v1/predict",
        json={
            "trackerId": "TRK-0002",
            "thresholdTemp": 8.0,
            "window": [
                {"ts": "2026-01-01T00:00:00Z", "temperature": 4.0},
                {"ts": "2026-01-01T00:01:00Z", "temperature": 4.0},
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["willBreach"] is False
    assert body["predictedBreachAt"] is None
