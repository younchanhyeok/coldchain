package com.coldchain.tracker.repository;

import com.coldchain.tracker.domain.Tracker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackerRepository extends JpaRepository<Tracker, String> {
}
