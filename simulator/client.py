import asyncio

import aiohttp


class TrackerClient:
    """M5부터 트래커 등록·배송 생성/전이는 화주 JWT가 필요하다(리딩 전송은 그대로 디바이스 키).
    최초 1회 로그인해 access 토큰을 붙이고, 만료(401) 시 재로그인 후 재시도한다 —
    장시간 실행되는 배송완료 전이(route-minutes 기본 30분)가 access 30분 만료에 걸릴 수 있어서다.

    M6에서 requests 동기 클라이언트를 aiohttp로 전환 — 순차 루프는 트래커 수백 개부터
    전송 주기를 못 지켜 부하 발생기 역할을 할 수 없다(서버가 아니라 클라이언트가 병목이 됨).
    단일 이벤트 루프에서 트래커당 코루틴 하나로 동시 전송한다. async context manager로 쓴다:
        async with TrackerClient(...) as client: ...
    """

    def __init__(self, base_url: str, email: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.access_token = None
        self._session: aiohttp.ClientSession | None = None
        # 토큰 만료 시 수천 코루틴이 동시에 401을 맞는다 — 재로그인은 락 안에서 한 번만 하고,
        # 락을 얻었을 때 토큰이 이미 바뀌어 있으면(다른 코루틴이 먼저 갱신) 그대로 재사용한다.
        self._relogin_lock = asyncio.Lock()

    async def __aenter__(self) -> "TrackerClient":
        # limit=0(무제한) — 기본값 100이면 커넥션 풀 대기가 클라이언트 쪽 병목이 되어
        # 서버 지연 측정을 오염시킨다. FD 상한은 loadtest.sh에서 ulimit으로 올린다.
        connector = aiohttp.TCPConnector(limit=0)
        self._session = aiohttp.ClientSession(
            connector=connector, timeout=aiohttp.ClientTimeout(total=5))
        await self._login()
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        await self._session.close()

    async def _login(self) -> None:
        async with self._session.post(
                f"{self.base_url}/api/v1/auth/login",
                json={"email": self.email, "password": self.password}) as resp:
            resp.raise_for_status()
            self.access_token = (await resp.json())["accessToken"]

    async def _relogin_if_stale(self, stale_token: str) -> None:
        async with self._relogin_lock:
            if self.access_token == stale_token:
                await self._login()

    async def _authed_json(self, method: str, path: str, payload: dict) -> dict:
        token = self.access_token
        async with self._session.request(
                method, f"{self.base_url}{path}", json=payload,
                headers={"Authorization": f"Bearer {token}"}) as resp:
            if resp.status != 401:
                resp.raise_for_status()
                return await resp.json()
        await self._relogin_if_stale(token)
        async with self._session.request(
                method, f"{self.base_url}{path}", json=payload,
                headers={"Authorization": f"Bearer {self.access_token}"}) as resp:
            resp.raise_for_status()
            return await resp.json()

    async def register_tracker(self, tracker_id: str, product_name: str, threshold_temp: float) -> dict:
        return await self._authed_json(
            "POST", "/api/v1/trackers",
            {"trackerId": tracker_id, "productName": product_name, "thresholdTemp": threshold_temp})

    async def create_shipment(self, tracker_id: str, product_name: str, origin: dict, destination: dict) -> dict:
        return await self._authed_json(
            "POST", "/api/v1/shipments",
            {
                "trackerId": tracker_id,
                "productName": product_name,
                "origin": origin,
                "destination": destination,
            })

    async def transition_shipment(self, shipment_id: int, status: str) -> dict:
        return await self._authed_json(
            "PATCH", f"/api/v1/shipments/{shipment_id}", {"status": status})

    async def send_reading(self, tracker_id: str, device_key: str, temperature: float, lat: float, lon: float,
                           recorded_at: str, seq: int) -> int:
        """상태코드를 반환하고 4xx/5xx에도 raise하지 않는다 — 부하 중 에러 응답도 측정 대상이다.
        디바이스 키 경로는 M5 이전과 동일 — permitAll, JWT 불필요."""
        async with self._session.post(
                f"{self.base_url}/api/v1/trackers/{tracker_id}/readings",
                json={
                    "temperature": temperature,
                    "lat": lat,
                    "lon": lon,
                    "recordedAt": recorded_at,
                    "seq": seq,
                },
                headers={"X-Device-Key": device_key}) as resp:
            await resp.read()
            return resp.status

    async def send_readings_batch(self, tracker_id: str, device_key: str, readings: list[dict]) -> int:
        """배치 전송(M6) — 같은 URL에 배열 body. 디바이스가 버퍼링했다 모아 보내는 형상의 재현."""
        async with self._session.post(
                f"{self.base_url}/api/v1/trackers/{tracker_id}/readings",
                json=readings,
                headers={"X-Device-Key": device_key}) as resp:
            await resp.read()
            return resp.status
