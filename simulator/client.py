import requests


class TrackerClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def register_tracker(self, tracker_id: str, product_name: str, threshold_temp: float) -> dict:
        resp = requests.post(
            f"{self.base_url}/api/v1/trackers",
            json={"trackerId": tracker_id, "productName": product_name, "thresholdTemp": threshold_temp},
            timeout=5,
        )
        resp.raise_for_status()
        return resp.json()

    def create_shipment(self, tracker_id: str, product_name: str, origin: dict, destination: dict) -> dict:
        resp = requests.post(
            f"{self.base_url}/api/v1/shipments",
            json={
                "trackerId": tracker_id,
                "productName": product_name,
                "origin": origin,
                "destination": destination,
            },
            timeout=5,
        )
        resp.raise_for_status()
        return resp.json()

    def transition_shipment(self, shipment_id: int, status: str) -> dict:
        resp = requests.patch(
            f"{self.base_url}/api/v1/shipments/{shipment_id}",
            json={"status": status},
            timeout=5,
        )
        resp.raise_for_status()
        return resp.json()

    def send_reading(self, tracker_id: str, device_key: str, temperature: float, lat: float, lon: float,
                      recorded_at: str, seq: int) -> requests.Response:
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
