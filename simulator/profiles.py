import math
import random

NOISE_SIGMA = 0.15
DEFAULT_COOLING_RATE = 0.05  # 뉴턴 냉각 계수(k), 클수록 목표 온도로 빨리 수렴


class TemperatureProfile:
    """뉴턴 냉각법칙(discrete): T(t+dt) = ambient + (T(t) - ambient) * e^(-k*dt) + noise.
    ambient(주변온도)가 프로파일마다 다르게 변하며 급변/점진 시나리오를 만든다."""

    name = "base"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE):
        self.temperature = initial_temp
        self.cooling_rate = cooling_rate

    def ambient_at(self, elapsed_seconds: float) -> float:
        raise NotImplementedError

    def step(self, elapsed_seconds: float, dt_seconds: float) -> float:
        ambient = self.ambient_at(elapsed_seconds)
        self.temperature = ambient + (self.temperature - ambient) * math.exp(-self.cooling_rate * dt_seconds)
        self.temperature += random.gauss(0, NOISE_SIGMA)
        return round(self.temperature, 2)


class NormalProfile(TemperatureProfile):
    """정상 — ambient가 임계값 아래로 안정."""

    name = "normal"

    def ambient_at(self, elapsed_seconds: float) -> float:
        return 4.0


class GradualRiseProfile(TemperatureProfile):
    """완만한 상승 — 문 씰 열화 등으로 ambient가 서서히 올라간다. M4 예측 검증용 추세."""

    name = "gradual-rise"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 rise_per_minute: float = 0.3):
        super().__init__(initial_temp, cooling_rate)
        self.rise_per_minute = rise_per_minute

    def ambient_at(self, elapsed_seconds: float) -> float:
        return 4.0 + self.rise_per_minute * (elapsed_seconds / 60.0)


class SuddenFailureProfile(TemperatureProfile):
    """급변(냉동기 고장) — 정상 유지하다 트리거 시점에 ambient가 급등하는 이산 이벤트."""

    name = "sudden-failure"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 failure_at_seconds: float = 120.0, failure_ambient: float = 25.0):
        super().__init__(initial_temp, cooling_rate)
        self.failure_at_seconds = failure_at_seconds
        self.failure_ambient = failure_ambient

    def ambient_at(self, elapsed_seconds: float) -> float:
        return 4.0 if elapsed_seconds < self.failure_at_seconds else self.failure_ambient


PROFILES = {
    NormalProfile.name: NormalProfile,
    GradualRiseProfile.name: GradualRiseProfile,
    SuddenFailureProfile.name: SuddenFailureProfile,
}
