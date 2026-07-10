import requests


class TrackerClient:
    """M5부터 트래커 등록·배송 생성/전이는 화주 JWT가 필요하다(리딩 전송은 그대로 디바이스 키).
    최초 1회 로그인해 access 토큰을 붙이고, 만료(401) 시 한 번 재로그인 후 재시도한다 —
    장시간 실행되는 배송완료 전이(route-minutes 기본 30분)가 access 30분 만료에 걸릴 수 있어서다.
    """

    def __init__(self, base_url: str, email: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.access_token = None
        self._login()

    def _login(self) -> None:
        resp = requests.post(
            f"{self.base_url}/api/v1/auth/login",
            json={"email": self.email, "password": self.password},
            timeout=5,
        )
        resp.raise_for_status()
        self.access_token = resp.json()["accessToken"]

    def _authed_request(self, method: str, url: str, **kwargs) -> requests.Response:
        headers = {"Authorization": f"Bearer {self.access_token}"}
        resp = requests.request(method, url, headers=headers, timeout=5, **kwargs)
        if resp.status_code == 401:
            self._login()
            headers = {"Authorization": f"Bearer {self.access_token}"}
            resp = requests.request(method, url, headers=headers, timeout=5, **kwargs)
        return resp

    def register_tracker(self, tracker_id: str, product_name: str, threshold_temp: float) -> dict:
        resp = self._authed_request(
            "POST", f"{self.base_url}/api/v1/trackers",
            json={"trackerId": tracker_id, "productName": product_name, "thresholdTemp": threshold_temp},
        )
        resp.raise_for_status()
        return resp.json()

    def create_shipment(self, tracker_id: str, product_name: str, origin: dict, destination: dict) -> dict:
        resp = self._authed_request(
            "POST", f"{self.base_url}/api/v1/shipments",
            json={
                "trackerId": tracker_id,
                "productName": product_name,
                "origin": origin,
                "destination": destination,
            },
        )
        resp.raise_for_status()
        return resp.json()

    def transition_shipment(self, shipment_id: int, status: str) -> dict:
        resp = self._authed_request(
            "PATCH", f"{self.base_url}/api/v1/shipments/{shipment_id}",
            json={"status": status},
        )
        resp.raise_for_status()
        return resp.json()

    def send_reading(self, tracker_id: str, device_key: str, temperature: float, lat: float, lon: float,
                      recorded_at: str, seq: int) -> requests.Response:
        # 디바이스 키 경로는 M5 이전과 동일 — permitAll, JWT 불필요.
        return requests.post(
            f"{self.base_url}/api/v1/trackers/{tracker_id}/readings",
            json={
                "temperature": temperature,
                "lat": lat,
                "lon": lon,
                "recordedAt": recorded_at,
                "seq": seq,
            },
            headers={"X-Device-Key": device_key},
            timeout=5,
        )
