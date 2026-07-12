#!/usr/bin/env python3
"""SSE e2e 반영 지연 프로브(NFR-2: 수집→대시보드 1~2초).

화주 JWT로 /api/v1/stream을 구독해 reading 이벤트의 recordedAt(ts)→수신 시각 delta를 잰다.
시뮬레이터와 같은 머신에서 돌리므로 시계 오차가 없다(같은 clock으로 ts를 만들고 잰다).
수집 API가 202를 빨리 돌려줘도(특히 M6 Kafka 전환 후) 실제 반영이 늦으면 여기서 드러난다 —
"202는 빨라졌는데 e2e는?"에 답하는 유일한 수단.
"""
import argparse
import asyncio
import json
import os
import time
from datetime import datetime, timezone

import aiohttp

from metrics import MetricsCollector, format_summary

SUMMARY_INTERVAL_SECONDS = 10.0


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


async def consume_stream(session: aiohttp.ClientSession, target: str, token: str,
                         metrics: MetricsCollector, deadline: float | None):
    async with session.get(f"{target}/api/v1/stream", params={"token": token}) as resp:
        resp.raise_for_status()
        event_name = None
        async for raw in resp.content:
            line = raw.decode("utf-8").rstrip("\n")
            if line.startswith("event:"):
                event_name = line[len("event:"):].strip()
            elif line.startswith("data:") and event_name == "reading":
                data = json.loads(line[len("data:"):].strip())
                recorded_at = datetime.fromisoformat(data["ts"].replace("Z", "+00:00"))
                delta = (datetime.now(timezone.utc) - recorded_at).total_seconds()
                metrics.record("sse-e2e", 200, delta)
            elif line == "":
                event_name = None
            if deadline is not None and time.monotonic() > deadline:
                return


async def main_async(args):
    metrics = MetricsCollector(warmup_seconds=args.warmup)
    # sock_read만 제한 — SSE는 무기한 열려 있으므로 total 타임아웃을 걸면 안 된다.
    # heartbeat가 15초 주기라 60초 무수신이면 죽은 커넥션으로 보고 재연결한다.
    timeout = aiohttp.ClientTimeout(total=None, sock_connect=5, sock_read=60)
    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.post(f"{args.target}/api/v1/auth/login",
                                json={"email": args.email, "password": args.password}) as resp:
            resp.raise_for_status()
            token = (await resp.json())["accessToken"]

        if args.wait_for:
            await wait_for_file(args.wait_for)
        metrics.start_clock()
        deadline = time.monotonic() + args.duration if args.duration > 0 else None

        reporter = asyncio.create_task(report_loop(metrics))
        try:
            while deadline is None or time.monotonic() < deadline:
                try:
                    await consume_stream(session, args.target, token, metrics, deadline)
                except (aiohttp.ClientError, asyncio.TimeoutError) as e:
                    print(f"[SSE 재연결] {e}", flush=True)
                    await asyncio.sleep(1)
        finally:
            reporter.cancel()
            meta = {"target": args.target, "startedAt": datetime.now(timezone.utc).isoformat()}
            report = metrics.write_report(args.report_out, meta) if args.report_out else metrics.final_report(meta)
            print("\n" + format_summary(report), flush=True)


def main():
    parser = argparse.ArgumentParser(description="SSE e2e 반영 지연 프로브")
    parser.add_argument("--target", default="http://localhost:8080")
    parser.add_argument("--email", default="shipper-a@coldchain.local")
    parser.add_argument("--password", default="coldchain-a")
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
