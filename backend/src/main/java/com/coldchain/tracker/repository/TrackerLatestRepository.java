package com.coldchain.tracker.repository;

import com.coldchain.tracker.domain.TrackerLatest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackerLatestRepository extends JpaRepository<TrackerLatest, String> {
}
