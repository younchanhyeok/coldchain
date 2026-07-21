package com.coldchain.shipment.repository;

import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    boolean existsByTrackerIdAndStatusNot(String trackerId, ShipmentStatus status);

    List<Shipment> findByShipperIdAndStatus(Long shipperId, ShipmentStatus status);

    Optional<Shipment> findByTrackerIdAndStatusNot(String trackerId, ShipmentStatus status);

    // PATCH /shipments/{id} 소유권 검사 — 타사 shipment면 부재와 동일하게 취급(404, 존재 은닉).
    Optional<Shipment> findByIdAndShipperId(Long id, Long shipperId);

    // 화물 관리 탭 목록 — 상태 무관 전체(검색/상태 필터는 프론트 클라이언트 사이드, 화면_탭_구성.md)
    Page<Shipment> findByShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);

    // GET /summary 집계용 — 화주 전체 shipment(상태 무관)
    List<Shipment> findByShipperId(Long shipperId);

    // GET /admin/overview — 트래커당 비-DELIVERED shipment는 최대 1건(생성 시 existsByTrackerIdAndStatusNot로
    // 보장)이라 이 카운트가 곧 "활성 트래커 수"다.
    long countByStatusNot(ShipmentStatus status);

    // M7: 현재 위치(lat/lon)에서 운송 중 배송 목적지까지 남은 거리(m) — v2 예측 context.
    // IN_TRANSIT만 대상 — 미출발(READY) 배송은 "남은 거리"가 의미 없고, ConsigneeTrack의
    // ETA/잔여거리도 IN_TRANSIT에만 부여하는 것과 정합(코드리뷰 반영). 운송 중 배송·목적지
    // 좌표 없으면 null. ST_DistanceSphere는 구면 근사(WGS84, 지표 거리).
    @Query(value = """
            SELECT ST_DistanceSphere(s.destination_position, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
              FROM shipment s
             WHERE s.tracker_id = :trackerId AND s.status = 'IN_TRANSIT'
               AND s.destination_position IS NOT NULL
             LIMIT 1
            """, nativeQuery = true)
    Double findRemainingDistanceMeters(@Param("trackerId") String trackerId,
            @Param("lat") double lat, @Param("lon") double lon);
}
