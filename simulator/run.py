#!/usr/bin/env python3
import argparse
import os
import time
import uuid
from datetime import datetime, timezone

from client import TrackerClient
from profiles import PROFILES
from route import interpolate, load_route

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROUTE = os.path.join(SCRIPT_DIR, "routes", "default.csv")


def make_tracker_id(run_stamp: str, index: int) -> str:
    # 실행마다 유니크하게 생성 — 고정 순번(TRK-0001식)이면 재실행 시 전부 409(DUPLICATE_RESOURCE)가 난다.
    # 초 단위 타임스탬프만 쓰면 여러 시뮬레이터 프로세스를 거의 동시에 띄웠을 때 충돌할 수 있어
    # 짧은 랜덤 suffix를 덧붙인다.
    return f"TRK-{run_stamp}-{index:04d}"


def register_trackers(client: TrackerClient, count: int, profile_name: str, threshold: float,
                       waypoints, origin_name: str, destination_name: str) -> list[dict]:
    run_stamp = f"{int(time.time())}{uuid.uuid4().hex[:4]}"
    origin = {"lat": waypoints[0].lat, "lon": waypoints[0].lon, "name": origin_name}
    destination = {"lat": waypoints[-1].lat, "lon": waypoints[-1].lon, "name": destination_name}

    trackers = []
    for i in range(count):
        tracker_id = make_tracker_id(run_stamp, i)
        product_name = f"백신 A ({profile_name})"

        registration = client.register_tracker(tracker_id, product_name, threshold)
        shipment = client.create_shipment(tracker_id, product_name, origin, destination)
        client.transition_shipment(shipment["shipmentId"], "IN_TRANSIT")

        trackers.append({
            "trackerId": tracker_id,
            "deviceKey": registration["deviceKey"],
            "shipmentId": shipment["shipmentId"],
            "profile": PROFILES[profile_name](),
            "seq": 0,
            "startTime": time.monotonic(),
            "delivered": False,
        })
        print(f"[등록완료] {tracker_id}")

    return trackers


def run_loop(client: TrackerClient, trackers: list[dict], waypoints, interval: float, route_seconds: float):
    try:
        while True:
            for t in trackers:
                if t["delivered"]:
                    continue

                elapsed = time.monotonic() - t["startTime"]
                temperature = t["profile"].step(elapsed, interval)
                progress = min(elapsed / route_seconds, 1.0) if route_seconds > 0 else 1.0
                lat, lon = interpolate(waypoints, progress)
                t["seq"] += 1
                recorded_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

                response = client.send_reading(
                    t["trackerId"], t["deviceKey"], temperature, lat, lon, recorded_at, t["seq"])
                marker = "OK" if response.status_code == 202 else f"ERR({response.status_code})"
                print(f"{t['trackerId']} temp={temperature:6.2f} seq={t['seq']:4d} {marker}")

                # 목적지 도달 — 배송 완료 처리 후 이 트래커는 더 이상 리딩을 보내지 않는다
                # (GET /summary의 deliveredCount·avgDeliveryMinutes가 실제로 채워지려면 필요).
                # 전이 호출이 일시 실패해도 루프 전체를 죽이지 않는다 — delivered를 세우지 않고
                # 다음 틱에 재시도한다(전이는 멱등하지 않지만 성공 전까지만 반복되므로 안전).
                if progress >= 1.0:
                    try:
                        client.transition_shipment(t["shipmentId"], "DELIVERED")
                        t["delivered"] = True
                        print(f"[배송완료] {t['trackerId']}")
                    except Exception as e:
                        print(f"[배송완료 전이 실패, 다음 틱에 재시도] {t['trackerId']}: {e}")

            time.sleep(interval)
    except KeyboardInterrupt:
        print("\n중단됨")


def main():
    parser = argparse.ArgumentParser(description="콜드체인 IoT 트래커 시뮬레이터")
    parser.add_argument("--trackers", type=int, default=5, help="가상 트래커 개수")
    parser.add_argument("--interval", type=float, default=5.0, help="전송 주기(초)")
    parser.add_argument("--profile", choices=PROFILES.keys(), default="normal")
    parser.add_argument("--target", default="http://localhost:8080", help="백엔드 base URL")
    parser.add_argument("--route", default=DEFAULT_ROUTE, help="경로 CSV 경로 (lat,lon 컬럼)")
    parser.add_argument("--route-minutes", type=float, default=30.0, help="경로 전체를 주파하는 데 걸리는 시간(분)")
    parser.add_argument("--threshold", type=float, default=8.0, help="임계 온도(℃)")
    parser.add_argument("--origin-name", default="성남 물류센터", help="출발지 표시명")
    parser.add_argument("--destination-name", default="서울대병원 약제부", help="도착지 표시명")
    args = parser.parse_args()

    waypoints = load_route(args.route)
    client = TrackerClient(args.target)

    trackers = register_trackers(
        client, args.trackers, args.profile, args.threshold, waypoints, args.origin_name, args.destination_name)
    print(f"\n{len(trackers)}개 트래커 등록 완료 — {args.interval}초 주기로 전송 시작 (Ctrl+C로 중단)\n")

    run_loop(client, trackers, waypoints, args.interval, args.route_minutes * 60)


if __name__ == "__main__":
    main()
