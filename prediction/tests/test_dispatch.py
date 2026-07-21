"""모델 디스패처(main.py PREDICTION_MODEL) 검증 — env가 실제로 v1/v2 라우팅을 바꾸는지,
미지 값은 fail-fast로 기동 거부하는지. 디스패처는 모듈 로드 시 결정하므로 importlib.reload로 재현."""
import importlib

import pytest


def _reload_main(monkeypatch, model_value):
    if model_value is None:
        monkeypatch.delenv("PREDICTION_MODEL", raising=False)
    else:
        monkeypatch.setenv("PREDICTION_MODEL", model_value)
    import app.main as main
    return importlib.reload(main)


def test_default_is_v1(monkeypatch):
    main = _reload_main(monkeypatch, None)
    assert main._predict.__module__ == "app.model"


def test_v2_env_routes_to_v2(monkeypatch):
    main = _reload_main(monkeypatch, "v2")
    assert main._predict.__module__ == "app.model_v2"


def test_case_insensitive(monkeypatch):
    main = _reload_main(monkeypatch, "V2")
    assert main._predict.__module__ == "app.model_v2"


def test_unknown_model_fails_fast(monkeypatch):
    # 오타가 조용히 v1으로 도는 게 아니라 기동 거부 — A/B 지표 오염 방지.
    with pytest.raises(RuntimeError, match="PREDICTION_MODEL"):
        _reload_main(monkeypatch, "v2-newton")


@pytest.fixture(autouse=True)
def _restore_main(monkeypatch):
    # 각 테스트가 main을 reload하므로, 세션 뒤 다른 테스트가 쓰는 app 상태를 기본(v1)으로 되돌린다.
    yield
    monkeypatch.delenv("PREDICTION_MODEL", raising=False)
    import app.main as main
    importlib.reload(main)
