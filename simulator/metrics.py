"""부하 측정 수집기 — 샘플별 (상대시각, 라벨, 상태코드, 소요초)를 모아
처리량·에러율·p50/p95/p99를 계산한다. run.py(수집 지연)·sse_probe.py(e2e 반영 지연)·
api_probe.py(대시보드 조회 지연)가 공유한다.

측정 시계는 start_clock() 호출(부하 시작) 기준이고, 호출 전에 record()가 먼저 오면
그 시점이 시작이다. 워밍업 구간(JIT·커넥션 풀 예열)은 최종 요약에서 제외한다.
"""
import json
import os
import time

OK_STATUSES = frozenset({200, 201, 202})


def _percentile(sorted_values: list[float], q: float) -> float:
    idx = min(int(len(sorted_values) * q), len(sorted_values) - 1)
    return sorted_values[idx]


class MetricsCollector:

    def __init__(self, warmup_seconds: float = 0.0):
        self.warmup_seconds = warmup_seconds
        self._start: float | None = None
        # (start 기준 상대시각, 라벨, 상태코드(예외는 0), 소요초)
        self._samples: list[tuple[float, str, int, float]] = []
        self._window_idx = 0

    def start_clock(self) -> None:
        if self._start is None:
            self._start = time.monotonic()

    def record(self, label: str, status: int, seconds: float) -> None:
        self.start_clock()
        self._samples.append((time.monotonic() - self._start, label, status, seconds))

    def window_report(self, window_seconds: float) -> str | None:
        """직전 호출 이후 샘플의 한 줄 요약 — 주기 출력용(리딩별 전량 print는 초당 수백 줄이
        되어 클라이언트 자체를 병목으로 만든다)."""
        new = self._samples[self._window_idx:]
        self._window_idx = len(self._samples)
        if self._start is None or not new:
            return None
        t_now = time.monotonic() - self._start
        parts = []
        for label in sorted({s[1] for s in new}):
            subset = [s for s in new if s[1] == label]
            ok_latencies = sorted(s[3] for s in subset if s[2] in OK_STATUSES)
            errors = len(subset) - len(ok_latencies)
            latency = (f"p50 {_percentile(ok_latencies, 0.5) * 1000:.0f}ms "
                       f"p99 {_percentile(ok_latencies, 0.99) * 1000:.0f}ms") if ok_latencies else "지연 -"
            parts.append(f"{label} {len(subset) / window_seconds:.1f}/s {latency} 에러 {errors}")
        return f"[t+{t_now:4.0f}s] " + " | ".join(parts)

    def final_report(self, meta: dict) -> dict:
        measured = [s for s in self._samples if s[0] >= self.warmup_seconds]
        span = (measured[-1][0] - self.warmup_seconds) if measured else 0.0
        labels = {}
        for label in sorted({s[1] for s in measured}):
            labels[label] = self._summarize([s for s in measured if s[1] == label], span)
        return {
            "meta": meta,
            "warmupSeconds": self.warmup_seconds,
            "measuredSeconds": round(span, 1),
            "labels": labels,
        }

    def write_report(self, path: str, meta: dict) -> dict:
        report = self.final_report(meta)
        directory = os.path.dirname(path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        return report

    @staticmethod
    def _summarize(samples: list[tuple[float, str, int, float]], span_seconds: float) -> dict:
        ok_latencies = sorted(s[3] for s in samples if s[2] in OK_STATUSES)
        status_counts: dict[str, int] = {}
        for s in samples:
            key = str(s[2]) if s[2] else "EXC"  # 0 = 타임아웃/커넥션 예외
            status_counts[key] = status_counts.get(key, 0) + 1
        summary = {
            "count": len(samples),
            "throughputPerSec": round(len(samples) / span_seconds, 2) if span_seconds > 0 else None,
            "errorRate": round((len(samples) - len(ok_latencies)) / len(samples), 4) if samples else None,
            "statusCounts": status_counts,
        }
        if ok_latencies:
            summary.update({
                "p50Ms": round(_percentile(ok_latencies, 0.50) * 1000, 1),
                "p95Ms": round(_percentile(ok_latencies, 0.95) * 1000, 1),
                "p99Ms": round(_percentile(ok_latencies, 0.99) * 1000, 1),
                "maxMs": round(ok_latencies[-1] * 1000, 1),
            })
        return summary


def format_summary(report: dict) -> str:
    lines = [f"측정 {report['measuredSeconds']}s (워밍업 {report['warmupSeconds']}s 제외)"]
    for label, s in report["labels"].items():
        if "p50Ms" in s:
            latency = (f"p50 {s['p50Ms']}ms p95 {s['p95Ms']}ms p99 {s['p99Ms']}ms max {s['maxMs']}ms")
        else:
            latency = "성공 샘플 없음"
        lines.append(f"  {label}: {s['count']}건 {s['throughputPerSec']}/s "
                     f"에러율 {s['errorRate']:.2%} | {latency} | status={s['statusCounts']}")
    return "\n".join(lines)
