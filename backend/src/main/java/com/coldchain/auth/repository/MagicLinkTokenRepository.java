package com.coldchain.auth.repository;

import com.coldchain.auth.domain.MagicLinkToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, String> {

    Optional<MagicLinkToken> findByShipmentId(Long shipmentId);
}
