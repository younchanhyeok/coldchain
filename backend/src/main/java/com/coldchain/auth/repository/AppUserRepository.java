package com.coldchain.auth.repository;

import com.coldchain.auth.domain.AppUser;
import com.coldchain.auth.domain.AppUserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    long countByRole(AppUserRole role);
}
