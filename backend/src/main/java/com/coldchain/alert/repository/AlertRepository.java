package com.coldchain.alert.repository;

import com.coldchain.alert.domain.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findByTrackerIdOrderByCreatedAtDesc(String trackerId, Pageable pageable);

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
