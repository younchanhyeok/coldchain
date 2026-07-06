import csv
from dataclasses import dataclass


@dataclass
class Waypoint:
    lat: float
    lon: float


def load_route(path: str) -> list[Waypoint]:
    waypoints = []
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            waypoints.append(Waypoint(float(row["lat"]), float(row["lon"])))
    if len(waypoints) < 2:
        raise ValueError(f"경로 CSV는 최소 2개 waypoint가 필요합니다: {path}")
    return waypoints


def interpolate(waypoints: list[Waypoint], progress: float) -> tuple[float, float]:
    """progress(0.0~1.0)에 따라 waypoint 사이를 선형 보간한다. 실제 도로 거리 기반이
    아니라 waypoint 인덱스 균등 분할이다 — M2 지도 시각화 전이라 이 정도로 충분하다."""
    progress = max(0.0, min(1.0, progress))
    segments = len(waypoints) - 1
    scaled = progress * segments
    index = min(int(scaled), segments - 1)
    local_t = scaled - index

    a, b = waypoints[index], waypoints[index + 1]
    lat = a.lat + (b.lat - a.lat) * local_t
    lon = a.lon + (b.lon - a.lon) * local_t
    return lat, lon
