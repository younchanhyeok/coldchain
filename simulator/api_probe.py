#!/usr/bin/env python3
"""대시보드 조회 API 지연 프로브 — 수집 부하가 걸린 상태에서 화주 대시보드가 얼마나
느려지는지 잰다. 프론트 폴링을 흉내내 GET /trackers·/summary·/shipments를 고정 주기로
호출한다 — 세 곳 모두 // PERF(M6) N+1 마커가 있어 1차 최적화(PR2)의 효과가 여기서 드러난다."""
import argparse
import asyncio
import os
import time
from datetime import datetime, timezone

import aiohttp

from metrics import MetricsCollector, format_summary

SUMMARY_INTERVAL_SECONDS = 10.0

ENDPOINTS = [
    ("trackers", "/api/v1/trackers"),
    ("summary", "/api/v1/summary"),
    ("shipments", "/api/v1/shipments"),
]


async def wait_for_file(path: str, timeout_seconds: float = 900.0):
    deadline = time.monotonic() + timeout_seconds
    while not os.path.exists(path):
        if time.monotonic() > deadline:
            raise TimeoutError(f"신호 파일 대기 시간 초과: {path}")
        await asyncio.sleep(0.5)


async def report_loop(metrics: MetricsCollector):
    while True:
        await asyncio.sleep(SUMMARY_INTERVAL_SECONDS)
        line = metrics.window_report(SUMMARY_INTERVAL_SECONDS)
        if line:
            print(line, flush=True)


async def main_async(args):
    metrics = MetricsCollector(warmup_seconds=args.warmup)
    timeout = aiohttp.ClientTimeout(total=30)  # 부하 중 대시보드가 수 초씩 걸리는 것 자체가 측정 대상 — 여유 있게
    async with aiohttp.ClientSession(timeout=timeout) as session:

        async def login() -> str:
            async with session.post(f"{args.target}/api/v1/auth/login",
                                    json={"email": args.email, "password": args.password}) as resp:
                resp.raise_for_status()
                return (await resp.json())["accessToken"]

        token = await login()

        if args.wait_for:
            await wait_for_file(args.wait_for)
        metrics.start_clock()
        deadline = time.monotonic() + args.duration if args.duration > 0 else None

        reporter = asyncio.create_task(report_loop(metrics))
        try:
            while deadline is None or time.monotonic() < deadline:
                tick_started = time.monotonic()
                for label, path in ENDPOINTS:
                    sent_at = time.monotonic()
                    try:
                        async with session.get(f"{args.target}{path}",
                                               headers={"Authorization": f"Bearer {token}"}) as resp:
                            await resp.read()
                            status = resp.status
                        if status == 401:
                            token = await login()
                    except Exception:
                        status = 0
                    metrics.record(label, status, time.monotonic() - sent_at)
                delay = args.poll_interval - (time.monotonic() - tick_started)
                if delay > 0:
                    await asyncio.sleep(delay)
        finally:
            reporter.cancel()
            meta = {"target": args.target, "pollIntervalSeconds": args.poll_interval,
                    "startedAt": datetime.now(timezone.utc).isoformat()}
            report = metrics.write_report(args.report_out, meta) if args.report_out else metrics.final_report(meta)
            print("\n" + format_summary(report), flush=True)


def main():
    parser = argparse.ArgumentParser(description="대시보드 조회 API 지연 프로브")
    parser.add_argument("--target", default="http://localhost:8080")
    parser.add_argument("--email", default="shipper-a@coldchain.local")
    parser.add_argument("--password", default="coldchain-a")
    parser.add_argument("--poll-interval", type=float, default=5.0, help="폴링 주기(초) — 프론트 폴링 흉내")
    parser.add_argument("--duration", type=float, default=0, help="측정 시간(초). 0이면 Ctrl+C까지")
    parser.add_argument("--warmup", type=float, default=0, help="워밍업 시간(초) — 최종 요약에서 제외")
    parser.add_argument("--report-out", default=None, help="측정 결과 JSON 저장 경로")
    parser.add_argument("--wait-for", default=None, help="측정 시작 전 대기할 신호 파일(run.py --ready-file)")
    args = parser.parse_args()

    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("\n중단됨")


if __name__ == "__main__":
    main()
