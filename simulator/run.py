#!/usr/bin/env python3
"""콜드체인 IoT 트래커 시뮬레이터 겸 부하 발생기(M6).

트래커당 코루틴 하나가 물리 곡선(뉴턴 냉각)을 그대로 유지한 채 고정 주기로 리딩을 보낸다 —
부하 중에도 L2/L3 체인이 실제로 반응해야 병목이 진짜로 드러나므로 부하 모드라고 곡선을
단순화하지 않는다. k6 같은 외부 도구를 안 쓰는 이유이기도 하다(등록→배송생성→IN_TRANSIT
도메인 플로우와 물리 곡선을 중복 구현해야 함).
"""
import argparse
import asyncio
import os
import random
import time
import uuid
from datetime import datetime, timezone

from client import TrackerClient
from metrics import MetricsCollector, format_summary
from profiles import PROFILES
from route import interpolate, load_route

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROUTE = os.path.join(SCRIPT_DIR, "routes", "default.csv")

# 초과 시 리딩별 로그를 끄고 10초 요약만 출력 — 초당 수백 줄 print는 클라이언트를 병목으로 만든다.
VERBOSE_MAX_TRACKERS = 50
SUMMARY_INTERVAL_SECONDS = 10.0
REGISTER_CONCURRENCY = 20  # 5,000개 × 3콜 순차 등록은 그 자체로 수 분 — 병렬화하되 등록으로 서버를 두들기진 않게 상한


def make_tracker_id(run_stamp: str, index: int) -> str:
    # 실행마다 유니크하게 생성 — 고정 순번(TRK-0001식)이면 재실행 시 전부 409(DUPLICATE_RESOURCE)가 난다.
    # 초 단위 타임스탬프만 쓰면 여러 시뮬레이터 프로세스를 거의 동시에 띄웠을 때 충돌할 수 있어
    # 짧은 랜덤 suffix를 덧붙인다.
    return f"TRK-{run_stamp}-{index:04d}"


async def register_trackers(client: TrackerClient, args, waypoints) -> list[dict]:
    run_stamp = f"{int(time.time())}{uuid.uuid4().hex[:4]}"
    origin = {"lat": waypoints[0].lat, "lon": waypoints[0].lon, "name": args.origin_name}
    destination = {"lat": waypoints[-1].lat, "lon": waypoints[-1].lon, "name": args.destination_name}
    verbose = args.trackers <= VERBOSE_MAX_TRACKERS
    semaphore = asyncio.Semaphore(REGISTER_CONCURRENCY)

    async def register_one(index: int) -> dict:
        async with semaphore:
            tracker_id = make_tracker_id(run_stamp, index)
            product_name = f"백신 A ({args.profile})"

            registration = await client.register_tracker(tracker_id, product_name, args.threshold)
            shipment = await client.create_shipment(tracker_id, product_name, origin, destination)
            await client.transition_shipment(shipment["shipmentId"], "IN_TRANSIT")

            # 트래커별 독립 rng — asyncio 인터리빙 순서와 무관하게 --seed면 곡선이 결정적이다.
            rng = random.Random(args.seed * 1_000_003 + index) if args.seed is not None else None
            if verbose:
                print(f"[등록완료] {tracker_id}")
            return {
                "trackerId": tracker_id,
                "deviceKey": registration["deviceKey"],
                "shipmentId": shipment["shipmentId"],
                "profile": PROFILES[args.profile](rng=rng),
                "seq": 0,
                "startTime": time.monotonic(),
                "delivered": False,
            }

    return list(await asyncio.gather(*(register_one(i) for i in range(args.trackers))))


async def tracker_loop(client: TrackerClient, t: dict, waypoints, interval: float, route_seconds: float,
                       metrics: MetricsCollector, verbose: bool, batch_size: int):
    # 시작 지터 — 전 트래커가 같은 틱에 몰리는 thundering herd 방지(실제 디바이스도 동기화돼 있지 않다).
    await asyncio.sleep(random.uniform(0, interval))
    t["startTime"] = time.monotonic()
    tick = 0
    buffer: list[dict] = []  # --batch-size > 1일 때 디바이스 버퍼링 재현

    while not t["delivered"]:
        elapsed = time.monotonic() - t["startTime"]
        temperature = t["profile"].step(elapsed, interval)
        progress = min(elapsed / route_seconds, 1.0) if route_seconds > 0 else 1.0
        lat, lon = interpolate(waypoints, progress)
        t["seq"] += 1
        recorded_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

        status = None  # 이번 틱에 HTTP 요청이 없었으면(버퍼 적재만) None
        sent_at = time.monotonic()
        try:
            if batch_size <= 1:
                status = await client.send_reading(
                    t["trackerId"], t["deviceKey"], temperature, lat, lon, recorded_at, t["seq"])
            else:
                buffer.append({"temperature": temperature, "lat": lat, "lon": lon,
                               "recordedAt": recorded_at, "seq": t["seq"]})
                if len(buffer) >= batch_size:
                    status = await client.send_readings_batch(t["trackerId"], t["deviceKey"], buffer)
                    buffer = []
        except Exception as e:
            status = 0  # 타임아웃/커넥션 예외 — 측정에선 에러로 집계
            if verbose:
                print(f"{t['trackerId']} 전송 실패: {e}")
        if status is not None:
            # 배치 모드에선 요청 단위 샘플 — 리딩 처리량은 throughput × batch_size (meta에 기록)
            metrics.record("ingest", status, time.monotonic() - sent_at)

        if verbose and status is not None:
            marker = "OK" if status == 202 else f"ERR({status})"
            print(f"{t['trackerId']} temp={temperature:6.2f} seq={t['seq']:4d} {marker}")

        # 목적지 도달 — 배송 완료 처리 후 이 트래커는 더 이상 리딩을 보내지 않는다
        # (GET /summary의 deliveredCount·avgDeliveryMinutes가 실제로 채워지려면 필요).
        # 전이 호출이 일시 실패해도 루프 전체를 죽이지 않는다 — delivered를 세우지 않고
        # 다음 틱에 재시도한다(전이는 멱등하지 않지만 성공 전까지만 반복되므로 안전).
        if progress >= 1.0:
            try:
                await client.transition_shipment(t["shipmentId"], "DELIVERED")
                t["delivered"] = True
                if verbose:
                    print(f"[배송완료] {t['trackerId']}")
            except Exception as e:
                if verbose:
                    print(f"[배송완료 전이 실패, 다음 틱에 재시도] {t['trackerId']}: {e}")

        # 고정 주기 스케줄 — sleep(interval) 누적 드리프트 방지. 응답이 interval보다 느려
        # 밀리면 지연분은 건너뛰고 즉시 다음 틱을 보낸다(백로그를 안 쌓음) — 이때 명목 전송률보다
        # 실측 처리량이 낮아지는 것 자체가 "무너짐" 신호다.
        tick += 1
        delay = t["startTime"] + tick * interval - time.monotonic()
        if delay > 0:
            await asyncio.sleep(delay)


async def report_loop(metrics: MetricsCollector):
    while True:
        await asyncio.sleep(SUMMARY_INTERVAL_SECONDS)
        line = metrics.window_report(SUMMARY_INTERVAL_SECONDS)
        if line:
            print(line, flush=True)


async def main_async(args):
    random.seed(args.seed)  # 지터 재현성 — 트래커별 곡선 rng는 등록 시 시드 파생으로 별도 생성
    waypoints = load_route(args.route)
    verbose = args.trackers <= VERBOSE_MAX_TRACKERS

    async with TrackerClient(args.target, args.email, args.password) as client:
        register_started = time.monotonic()
        trackers = await register_trackers(client, args, waypoints)
        print(f"\n{len(trackers)}개 트래커 등록 완료 ({time.monotonic() - register_started:.1f}s) — "
              f"{args.interval}초 주기로 전송 시작 (Ctrl+C로 중단)\n", flush=True)

        if args.ready_file:
            # 부하 시작 신호 — loadtest.sh의 프로브들이 이 파일을 기다렸다가 측정 시계를 맞춘다
            # (등록에 걸리는 시간이 트래커 수에 비례해 달라지므로 프로세스 시작 시각은 기준이 못 된다).
            with open(args.ready_file, "w") as f:
                f.write(datetime.now(timezone.utc).isoformat())

        metrics = MetricsCollector(warmup_seconds=args.warmup)
        metrics.start_clock()
        tasks = [
            asyncio.create_task(tracker_loop(
                client, t, waypoints, args.interval, args.route_minutes * 60, metrics, verbose,
                args.batch_size))
            for t in trackers
        ]
        reporter = asyncio.create_task(report_loop(metrics))

        try:
            if args.duration > 0:
                await asyncio.sleep(args.warmup + args.duration)
            else:
                await asyncio.gather(*tasks)  # 전 트래커 배송완료 시 종료(데모 모드 기존 동작)
        finally:
            reporter.cancel()
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)

            meta = {
                "trackers": args.trackers,
                "intervalSeconds": args.interval,
                "profile": args.profile,
                "seed": args.seed,
                "batchSize": args.batch_size,
                "target": args.target,
                "startedAt": datetime.now(timezone.utc).isoformat(),
            }
            report = metrics.write_report(args.report_out, meta) if args.report_out else metrics.final_report(meta)
            print("\n" + format_summary(report), flush=True)
            if args.report_out:
                print(f"리포트 저장: {args.report_out}", flush=True)


def main():
    parser = argparse.ArgumentParser(description="콜드체인 IoT 트래커 시뮬레이터 겸 부하 발생기")
    parser.add_argument("--trackers", type=int, default=5, help="가상 트래커 개수")
    parser.add_argument("--interval", type=float, default=5.0, help="전송 주기(초)")
    parser.add_argument("--profile", choices=PROFILES.keys(), default="normal")
    parser.add_argument("--target", default="http://localhost:8080", help="백엔드 base URL")
    parser.add_argument("--route", default=DEFAULT_ROUTE, help="경로 CSV 경로 (lat,lon 컬럼)")
    parser.add_argument("--route-minutes", type=float, default=30.0, help="경로 전체를 주파하는 데 걸리는 시간(분)")
    parser.add_argument("--threshold", type=float, default=8.0, help="임계 온도(℃)")
    parser.add_argument("--origin-name", default="성남 물류센터", help="출발지 표시명")
    parser.add_argument("--destination-name", default="서울대병원 약제부", help="도착지 표시명")
    parser.add_argument("--email", default="shipper-a@coldchain.local", help="화주 로그인 이메일(V8 시드 기본값)")
    parser.add_argument("--password", default="coldchain-a", help="화주 로그인 비밀번호(V8 시드 기본값)")
    parser.add_argument("--seed", type=int, default=None, help="온도 곡선·지터 시드(부하테스트 재현성)")
    parser.add_argument("--batch-size", type=int, default=1,
                        help="배치 전송 크기(기본 1=단건). >1이면 디바이스가 k건 버퍼링 후 배열 body로 전송 — "
                             "리딩 신선도(지연)를 희생하고 요청 수를 줄이는 변형 측정용(M6)")
    parser.add_argument("--duration", type=float, default=0,
                        help="측정 시간(초). 0이면 전 트래커 배송완료까지 실행(데모 모드)")
    parser.add_argument("--warmup", type=float, default=0,
                        help="워밍업 시간(초) — duration에 더해 실행되고 최종 요약에서 제외")
    parser.add_argument("--report-out", default=None, help="측정 결과 JSON 저장 경로")
    parser.add_argument("--ready-file", default=None, help="등록 완료(부하 시작) 시 생성할 신호 파일 — loadtest.sh용")
    args = parser.parse_args()

    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("\n중단됨")


if __name__ == "__main__":
    main()
