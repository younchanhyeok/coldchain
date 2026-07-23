#!/usr/bin/env python3
"""M7 v1 vs v2 예측 모델 비교 오케스트레이터.

원시 reading 보존이 8일(M6)이라 과거 리플레이가 불가능하므로, "결말을 아는 시나리오 세트"를
모델 토글로 두 번(짝지은 시드) 실행해 페이즈별 지표 스냅샷을 남기고 대조한다.

한 rep의 흐름:
  페이즈 A = PREDICTION_MODEL=v1 예측서버 기동 → 프로파일 동시 주행 → ACTIVE 예측 드레인 →
             수동 평가 런 스냅샷(모델버전 v1-linear 필터) → 예측서버 종료
  페이즈 B = 같은 시드로 v2(v2-newton) 반복 (곡선 동일 = 짝지은 비교)
rep마다 시드를 바꿔 여러 번 반복(안정성 예시 — 통계적 유의성 주장 아님).

정직성 한계(개발정리_M7에도 명시):
  - 시드가 같아 곡선 형상은 동일하나 벽시계 시간축은 페이즈마다 다르다(리드타임 절대값이 아닌
    형상 기준 비교).
  - 페이즈당 한 모델만 돌아, 수동 런의 modelVersion 필터가 그 모델 몫을 정확히 집계한다.
  - missedBreaches는 창 전역(모델 무필터)이라 프로파일별로 쪼개지 않고 집계값만 보고한다.

백엔드 변경 없음: 프로파일별 분해는 GET /admin/metrics/prediction의 episodes[]를
productName의 프로파일명("백신 A (plateau)")으로 그룹핑해 스크립트가 계산한다.

사용 예:
  python evaluate.py --admin-key <키> --reps 3 --trackers 10 --route-minutes 15 \
      --pred-python ../.venv-pred/bin/python --sim-python ../.venv-sim/bin/python
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPORTS_DIR = os.path.join(SCRIPT_DIR, "reports")

# 기본은 profiles.py의 전 프로파일(M7 4종 + M8 slow-rise/gentle-failure). --profiles로 부분 선택.
from profiles import PROFILES as PROFILE_REGISTRY  # noqa: E402 (run.py와 같은 디렉터리 실행 전제)

DEFAULT_PROFILES = ",".join(PROFILE_REGISTRY.keys())
# 결과 modelVersion 문자열 → 예측서버 기동 env. v1/v2 각 모델이 응답에 자기 버전을 새기므로
# 이 매핑이 "페이즈 env"와 "지표 필터"를 잇는다.
MODELS = [("v1", "v1-linear"), ("v2", "v2-newton")]

PROFILE_RE = re.compile(r"\(([^)]+)\)")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def log(msg: str) -> None:
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


# ── 어드민 API (X-Admin-Key만 필요, JWT 불필요) ──────────────────────────────
def admin_get(base: str, key: str, path: str, params: dict) -> object:
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(f"{base}{path}?{qs}", headers={"X-Admin-Key": key})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.load(r)


def admin_post(base: str, key: str, path: str, body: dict) -> object:
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{base}{path}", data=data, method="POST",
        headers={"X-Admin-Key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


# ── 예측서버 서브프로세스 (페이즈마다 env 바꿔 재기동) ───────────────────────
def start_prediction(pred_dir: str, python_exe: str, port: int, model_env: str, logf) -> subprocess.Popen:
    env = {**os.environ, "PREDICTION_MODEL": model_env}
    proc = subprocess.Popen(
        [python_exe, "-m", "uvicorn", "app.main:app", "--port", str(port)],
        cwd=pred_dir, env=env, stdout=logf, stderr=subprocess.STDOUT)
    return proc


def wait_healthz(port: int, timeout: float = 60.0) -> None:
    deadline = time.monotonic() + timeout
    url = f"http://localhost:{port}/healthz"
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=3) as r:
                if r.status == 200:
                    return
        except (urllib.error.URLError, ConnectionError, OSError):
            pass
        time.sleep(1.0)
    raise TimeoutError(f"예측서버 healthz가 {timeout}s 내에 뜨지 않음 (port {port})")


def stop_prediction(proc: subprocess.Popen) -> None:
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()


# ── 프로파일 동시 주행 ───────────────────────────────────────────────────────
def run_profiles(sim_python: str, args, seed: int, logf) -> None:
    duration = int(args.route_minutes * 60 + args.tail_seconds)
    procs = []
    for prof in args.profile_list:
        cmd = [
            sim_python, os.path.join(SCRIPT_DIR, "run.py"),
            "--profile", prof, "--trackers", str(args.trackers),
            "--interval", str(args.interval), "--route-minutes", str(args.route_minutes),
            "--duration", str(duration), "--seed", str(seed),
            "--target", args.target, "--email", args.email, "--password", args.password,
        ]
        procs.append(subprocess.Popen(cmd, cwd=SCRIPT_DIR, stdout=logf, stderr=subprocess.STDOUT))
    # 종료 코드를 확인한다 — run.py가 조용히 실패(로그인 오류·크래시)하면 그 프로파일 데이터가
    # 통째로 빠진 채 스냅샷이 만들어져 비교가 오염된다. 행(hang)에도 대비해 넉넉한 타임아웃 후 kill.
    for prof, p in zip(args.profile_list, procs):
        try:
            code = p.wait(timeout=duration * 2 + 120)
        except subprocess.TimeoutExpired:
            p.kill()
            p.wait()
            code = -1
        if code != 0:
            log(f"  경고: run.py [{prof}] 비정상 종료(code={code}) — 이 프로파일 데이터 누락 가능")


def drain_active(base: str, key: str, from_ts: str, model_version: str, cap_seconds: float) -> int:
    """ACTIVE 예측이 BREACHED/EXPIRED/CANCELED로 해소될 때까지 대기(cap까지). 남은 ACTIVE 수 반환.
    미해소 ACTIVE는 FP/TP로 안 잡혀 지표를 왜곡하므로 스냅샷 전에 최대한 비운다. v1·v2 모두
    같은 규칙·같은 cap을 받아 남는 게 있어도 짝지은 비교의 공정성은 유지된다."""
    deadline = time.monotonic() + cap_seconds
    last = -1
    while time.monotonic() < deadline:
        try:
            m = admin_get(base, key, "/api/v1/admin/metrics/prediction",
                          {"from": from_ts, "to": now_iso(), "modelVersion": model_version})
        except (urllib.error.URLError, OSError) as e:
            # 드레인 폴 중 일시적 백엔드 오류(바쁨·순간 끊김)로 2시간 런 전체를 죽이지 않는다.
            log(f"  드레인 폴 일시 오류(무시하고 재시도): {e}")
            time.sleep(20.0)
            continue
        active = sum(1 for e in m.get("episodes", []) if e.get("status") == "ACTIVE")
        if active != last:
            log(f"  드레인 대기: ACTIVE {active}건")
            last = active
        if active == 0:
            return 0
        time.sleep(20.0)
    return last if last > 0 else 0


def summarize_profiles(episodes: list) -> dict:
    """episodes[]를 프로파일별 상태 카운트로 분해(백엔드 지표 정의와 같은 관점).
    TP=BREACHED, FP=CANCELED+EXPIRED(만료·해제=미이탈 경고), INVALIDATED=급변 무효화(별도)."""
    by_profile: dict = {}
    for e in episodes:
        m = PROFILE_RE.search(e.get("productName", ""))
        prof = m.group(1) if m else "unknown"
        b = by_profile.setdefault(prof, {"BREACHED": 0, "CANCELED": 0, "EXPIRED": 0,
                                         "INVALIDATED": 0, "ACTIVE": 0, "leadTimes": []})
        st = e.get("status", "?")
        if st in b:
            b[st] += 1
        if st == "BREACHED" and e.get("leadTimeMinutes") is not None:
            b["leadTimes"].append(e["leadTimeMinutes"])
    for b in by_profile.values():
        tp, fp = b["BREACHED"], b["CANCELED"] + b["EXPIRED"]
        b["tp"], b["fp"] = tp, fp
        b["hitRate"] = round(tp / (tp + fp), 3) if (tp + fp) else None
        b["avgLead"] = round(sum(b["leadTimes"]) / len(b["leadTimes"]), 1) if b["leadTimes"] else None
    return by_profile


# ── 한 페이즈 = 한 모델 × 한 rep ────────────────────────────────────────────
def run_phase(args, model_env: str, model_version: str, rep: int, seed: int, logf) -> dict:
    log(f"[rep{rep}] {model_version} 예측서버 기동 (seed={seed})")
    proc = start_prediction(args.pred_dir, args.pred_python, args.pred_port, model_env, logf)
    try:
        wait_healthz(args.pred_port)
        log(f"[rep{rep}] {model_version} healthz OK — 백엔드 서킷 회복 {args.circuit_wait}s 대기")
        time.sleep(args.circuit_wait)

        from_ts = now_iso()
        log(f"[rep{rep}] {model_version} {len(args.profile_list)}프로파일 주행 시작 ({args.route_minutes}분 경로)")
        run_profiles(args.sim_python, args, seed, logf)
        log(f"[rep{rep}] {model_version} 주행 종료 — ACTIVE 드레인")
        remaining = drain_active(args.target, args.admin_key, from_ts, model_version, args.drain_cap)
        if remaining:
            log(f"[rep{rep}] {model_version} 경고: 미해소 ACTIVE {remaining}건 (cap 도달) — 지표 일부 미집계")
        to_ts = now_iso()

        run = admin_post(args.target, args.admin_key, "/api/v1/admin/evaluation-runs",
                         {"from": from_ts, "to": to_ts,
                          "label": f"{args.label_prefix}-rep{rep}-{model_version}",
                          "modelVersion": model_version})
        metrics = admin_get(args.target, args.admin_key, "/api/v1/admin/metrics/prediction",
                            {"from": from_ts, "to": to_ts, "modelVersion": model_version})
        log(f"[rep{rep}] {model_version} 런 저장 #{run['id']} — "
            f"TP={run['truePositives']} FP={run['falsePositives']} missed={run['missedBreaches']} "
            f"hitRate={run['hitRate']:.2f} fpRate={run['falsePositiveRate']:.2f}")
        return {"rep": rep, "modelVersion": model_version, "from": from_ts, "to": to_ts,
                "run": run, "byProfile": summarize_profiles(metrics.get("episodes", [])),
                "remainingActive": remaining}
    finally:
        stop_prediction(proc)
        time.sleep(2.0)


# ── 리포트 생성 ──────────────────────────────────────────────────────────────
def avg(vals: list):
    v = [x for x in vals if x is not None]
    return round(sum(v) / len(v), 2) if v else None


def build_markdown(results: list, args) -> str:
    def rows(mv: str):
        return [r for r in results if r["modelVersion"] == mv]

    def agg(mv: str, field: str):
        return avg([r["run"][field] for r in rows(mv)])

    # 실제 실행된 rep 수 — --start-rep로 일부만 이어받아 돌리면 args.reps보다 적다(과대 표기 금지).
    actual_reps = len({r["rep"] for r in results})
    lines = [
        "# 예측 모델 비교 — v1-linear vs v2-newton",
        "",
        f"- 실행: {now_iso()}",
        f"- 설정: {actual_reps} reps × {len(args.profile_list)} 프로파일 × {args.trackers} 트래커, "
        f"경로 {args.route_minutes}분, 임계 8.0℃",
        f"- 페이즈별 시드 짝지음(v1·v2 동일 곡선), 프로파일: {', '.join(args.profile_list)}",
        "",
        "## 종합 (rep 평균, 수동 평가 런 집계)",
        "",
        "| 지표 | v1-linear | v2-newton |",
        "|---|---|---|",
        f"| 예측 수 | {agg('v1-linear','totalPredictions')} | {agg('v2-newton','totalPredictions')} |",
        f"| 적중 TP | {agg('v1-linear','truePositives')} | {agg('v2-newton','truePositives')} |",
        f"| 오탐 FP | {agg('v1-linear','falsePositives')} | {agg('v2-newton','falsePositives')} |",
        f"| 놓침 missed | {agg('v1-linear','missedBreaches')} | {agg('v2-newton','missedBreaches')} |",
        f"| 적중률 | {agg('v1-linear','hitRate')} | {agg('v2-newton','hitRate')} |",
        f"| 오탐률 | {agg('v1-linear','falsePositiveRate')} | {agg('v2-newton','falsePositiveRate')} |",
        f"| 평균 리드타임(분) | {agg('v1-linear','avgLeadTimeMinutes')} | {agg('v2-newton','avgLeadTimeMinutes')} |",
        f"| 시각오차(분) | {agg('v1-linear','avgBreachTimingErrorMinutes')} | {agg('v2-newton','avgBreachTimingErrorMinutes')} |",
        "",
        "## 프로파일별 분해 (episodes 그룹핑, rep 합산)",
        "",
        "| 프로파일 | 모델 | TP | FP | 적중률 | 평균 리드타임 |",
        "|---|---|---|---|---|---|",
    ]
    for prof in args.profile_list:
        for _, mv in MODELS:
            tp = sum(r["byProfile"].get(prof, {}).get("tp", 0) for r in rows(mv))
            fp = sum(r["byProfile"].get(prof, {}).get("fp", 0) for r in rows(mv))
            leads = [x for r in rows(mv) for x in r["byProfile"].get(prof, {}).get("leadTimes", [])]
            hit = round(tp / (tp + fp), 3) if (tp + fp) else "—"
            lead = round(sum(leads) / len(leads), 1) if leads else "—"
            lines.append(f"| {prof} | {mv} | {tp} | {fp} | {hit} | {lead} |")
    lines += [
        "",
        "> 한계: 시드는 같지만 벽시계 시간축이 페이즈마다 달라 리드타임 절대값이 아닌 형상으로 읽는다. "
        "missedBreaches는 창 전역 집계라 프로파일별로 쪼개지 않았다. reps는 안정성 예시이며 "
        "통계적 유의성 주장이 아니다.",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="M7 v1 vs v2 비교 오케스트레이터")
    parser.add_argument("--admin-key", required=True, help="백엔드 ADMIN_KEY (수동 런·지표 조회)")
    parser.add_argument("--target", default="http://localhost:8080", help="백엔드 base URL")
    parser.add_argument("--reps", type=int, default=3, help="반복 횟수(rep마다 시드 변경)")
    parser.add_argument("--start-rep", type=int, default=0,
                        help="시작 rep 인덱스 — 중단된 런 이어받기용(예: --start-rep 2 --reps 3 = rep2만)")
    parser.add_argument("--base-seed", type=int, default=42, help="rep 0 시드(rep r = base+r)")
    parser.add_argument("--profiles", default=DEFAULT_PROFILES,
                        help=f"쉼표 구분 프로파일 목록(기본: 전체 = {DEFAULT_PROFILES})")
    parser.add_argument("--label-prefix", default="bench",
                        help="수동 평가 런 라벨 접두(기본 bench) — 마일스톤별로 m7/m8 등 구분용")
    parser.add_argument("--trackers", type=int, default=10, help="프로파일당 트래커 수")
    parser.add_argument("--interval", type=float, default=5.0, help="전송 주기(초)")
    parser.add_argument("--route-minutes", type=float, default=15.0, help="경로 주파(분) = 페이즈 길이")
    parser.add_argument("--tail-seconds", type=float, default=60.0, help="배송완료 후 여유 주행(초)")
    parser.add_argument("--drain-cap", type=float, default=360.0, help="ACTIVE 드레인 최대 대기(초)")
    parser.add_argument("--circuit-wait", type=float, default=30.0, help="예측서버 재기동 후 서킷 회복 대기(초)")
    parser.add_argument("--pred-dir", default=os.path.join(SCRIPT_DIR, "..", "prediction"),
                        help="예측서버 디렉터리(uvicorn cwd)")
    parser.add_argument("--pred-python", default=sys.executable, help="예측서버 python(fastapi/uvicorn/sklearn)")
    parser.add_argument("--sim-python", default=sys.executable, help="시뮬레이터 python(aiohttp)")
    parser.add_argument("--pred-port", type=int, default=8000, help="예측서버 포트")
    parser.add_argument("--email", default="shipper-a@coldchain.local")
    parser.add_argument("--password", default="coldchain-a")
    args = parser.parse_args()
    if not (0 <= args.start_rep < args.reps):
        parser.error("--start-rep는 0 이상 --reps 미만이어야 합니다(빈 실행 방지)")
    args.profile_list = [p.strip() for p in args.profiles.split(",") if p.strip()]
    unknown = [p for p in args.profile_list if p not in PROFILE_REGISTRY]
    if unknown or not args.profile_list:
        parser.error(f"--profiles에 미지 프로파일: {unknown} (가능: {DEFAULT_PROFILES})")
    # slow-rise는 임계 도달이 ~27분 — 경로가 그보다 짧으면 이탈이 경로 안에 안 들어와 예측이 전부
    # 오탐(FP)으로 집계되어 비교가 조용히 오염된다. 스모크(짧은 경로)는 허용하되 크게 경고한다.
    if "slow-rise" in args.profile_list and args.route_minutes < 28:
        log(f"⚠ 경고: slow-rise는 임계 도달 ~27분인데 --route-minutes={args.route_minutes}(<28)라 "
            f"이탈이 경로 안에 안 들어온다 → slow-rise 예측이 전부 FP로 집계됨. 비교 목적이면 30+ 권장.")
    args.pred_dir = os.path.abspath(args.pred_dir)

    os.makedirs(REPORTS_DIR, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    log_path = os.path.join(REPORTS_DIR, f"compare-{stamp}.log")
    logf = open(log_path, "w")
    log(f"로그: {log_path}")

    # 사전 점검 — 어드민 키·백엔드 M7 엔드포인트 유효성 먼저 확인(2h 돌린 뒤 401/404 방지).
    try:
        admin_get(args.target, args.admin_key, "/api/v1/admin/evaluation-runs", {"limit": 1})
        log("사전 점검 OK — 어드민 키·평가 런 엔드포인트 유효")
    except urllib.error.HTTPError as e:
        log(f"사전 점검 실패 ({e.code}) — 어드민 키/백엔드 M7 빌드 확인 필요")
        sys.exit(1)

    results = []
    total = (args.reps - args.start_rep) * len(MODELS)
    done = 0
    start = time.monotonic()
    for rep in range(args.start_rep, args.reps):
        seed = args.base_seed + rep
        for model_env, model_version in MODELS:
            results.append(run_phase(args, model_env, model_version, rep, seed, logf))
            done += 1
            elapsed = (time.monotonic() - start) / 60.0
            log(f"진행 {done}/{total} 페이즈 완료 (누적 {elapsed:.1f}분)")
            # 중간 산출물 저장 — 중단돼도 여기까지는 남는다.
            with open(os.path.join(REPORTS_DIR, f"compare-{stamp}.json"), "w") as f:
                json.dump(results, f, ensure_ascii=False, indent=2)

    md = build_markdown(results, args)
    md_path = os.path.join(REPORTS_DIR, f"compare-{stamp}.md")
    with open(md_path, "w") as f:
        f.write(md)
    logf.close()
    log(f"완료 — 리포트: {md_path}")
    print("\n" + md)


if __name__ == "__main__":
    main()
