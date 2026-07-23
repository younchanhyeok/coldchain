import math
import random

NOISE_SIGMA = 0.15
DEFAULT_COOLING_RATE = 0.05  # 뉴턴 냉각 계수(k), 클수록 목표 온도로 빨리 수렴


class TemperatureProfile:
    """뉴턴 냉각법칙(discrete): T(t+dt) = ambient + (T(t) - ambient) * e^(-k*dt) + noise.
    ambient(주변온도)가 프로파일마다 다르게 변하며 급변/점진 시나리오를 만든다.

    rng: 트래커별 random.Random 인스턴스(없으면 전역 random) — 부하테스트 --seed 재현성용.
    전역 시드만으로는 asyncio 인터리빙 순서에 따라 트래커별 곡선이 달라진다.
    """

    name = "base"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 rng: random.Random | None = None):
        self.temperature = initial_temp
        self.cooling_rate = cooling_rate
        self.rng = rng if rng is not None else random

    def ambient_at(self, elapsed_seconds: float) -> float:
        raise NotImplementedError

    def step(self, elapsed_seconds: float, dt_seconds: float) -> float:
        ambient = self.ambient_at(elapsed_seconds)
        self.temperature = ambient + (self.temperature - ambient) * math.exp(-self.cooling_rate * dt_seconds)
        self.temperature += self.rng.gauss(0, NOISE_SIGMA)
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
                 rise_per_minute: float = 0.3, rng: random.Random | None = None):
        super().__init__(initial_temp, cooling_rate, rng)
        self.rise_per_minute = rise_per_minute

    def ambient_at(self, elapsed_seconds: float) -> float:
        return 4.0 + self.rise_per_minute * (elapsed_seconds / 60.0)


class SuddenFailureProfile(TemperatureProfile):
    """급변(냉동기 고장) — 정상 유지하다 트리거 시점에 ambient가 급등하는 이산 이벤트."""

    name = "sudden-failure"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 failure_at_seconds: float = 120.0, failure_ambient: float = 25.0,
                 rng: random.Random | None = None):
        super().__init__(initial_temp, cooling_rate, rng)
        self.failure_at_seconds = failure_at_seconds
        self.failure_ambient = failure_ambient

    def ambient_at(self, elapsed_seconds: float) -> float:
        return 4.0 if elapsed_seconds < self.failure_at_seconds else self.failure_ambient


class PlateauProfile(TemperatureProfile):
    """정체 — ambient가 상승하다 임계 바로 아래(ceiling)에서 점근한다. 실제 이탈은 없다.
    v1(온도-시간 선형)은 상승 구간만 보고 임계 도달을 예측(구조적 오탐)하지만, v2(뉴턴 냉각)는
    ambient≤threshold라 물리적으로 도달 불가로 판정한다 — v1 vs v2 오탐률 대비의 표적 시나리오.
    ceiling 7.0은 기본 임계 8.0 대비 노이즈 σ0.15의 >6σ 마진이라 이탈이 사실상 불가능하다."""

    name = "plateau"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 rise_per_minute: float = 0.3, ceiling: float = 7.0, rng: random.Random | None = None):
        super().__init__(initial_temp, cooling_rate, rng)
        self.rise_per_minute = rise_per_minute
        self.ceiling = ceiling

    def ambient_at(self, elapsed_seconds: float) -> float:
        return min(4.0 + self.rise_per_minute * (elapsed_seconds / 60.0), self.ceiling)


class SlowRiseProfile(GradualRiseProfile):
    """완만한 상승(M8) — gradual-rise의 절반 기울기(0.15℃/min). 리드타임 축 표적:
    기존 0.3/min은 30분 내 임계 도달이 빨라 경보가 이탈에 붙었고(M7 실측 리드타임 ~0),
    L2 급변 감지도 자주 발화해 INVALIDATED 재발령 루프를 만들었다. 절반 기울기 + 30분 경로면
    임계(8.0) 도달이 ~27분 — 예측이 수 분 앞서 경고할 시간축이 생긴다."""

    name = "slow-rise"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = DEFAULT_COOLING_RATE,
                 rise_per_minute: float = 0.15, rng: random.Random | None = None):
        super().__init__(initial_temp, cooling_rate, rise_per_minute, rng)


class GentleFailureProfile(SuddenFailureProfile):
    """완만한 고장(M8) — v2 재-activate(개선 b) 표적. 기존 sudden(25℃, τ=20s)은 고장→이탈이
    ~14s(3점)라 v2가 새 국면 5점을 모으기 전에 "이미 초과" 가드에 걸려 침묵했다(M7 실측).
    failure_ambient 11℃ + cooling_rate 0.02(τ=50s)면 고장→이탈 ≈ ln((11-4)/(11-8))/0.02 ≈ 42s
    ≈ 8점 — 무효화 후 새 국면 5점으로 재적합해 이탈 전에 다시 경고할 수 있는 시간이 생긴다."""

    name = "gentle-failure"

    def __init__(self, initial_temp: float = 5.0, cooling_rate: float = 0.02,
                 failure_at_seconds: float = 120.0, failure_ambient: float = 11.0,
                 rng: random.Random | None = None):
        super().__init__(initial_temp, cooling_rate, failure_at_seconds, failure_ambient, rng)


PROFILES = {
    NormalProfile.name: NormalProfile,
    GradualRiseProfile.name: GradualRiseProfile,
    SuddenFailureProfile.name: SuddenFailureProfile,
    PlateauProfile.name: PlateauProfile,
    SlowRiseProfile.name: SlowRiseProfile,
    GentleFailureProfile.name: GentleFailureProfile,
}
