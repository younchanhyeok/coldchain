package com.coldchain.shipment.repository;

import com.coldchain.shipment.domain.Shipment;
import com.coldchain.shipment.domain.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
