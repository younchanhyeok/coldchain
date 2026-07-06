package com.coldchain.reading.service;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.dto.ReadingPoint;
import com.coldchain.reading.dto.ReadingSeriesResponse;
import com.coldchain.reading.repository.ReadingRepository;
import com.coldchain.tracker.repository.TrackerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ReadingService {

    private final ReadingRepository readingRepository;
    private final TrackerRepository trackerRepository;

    public ReadingService(ReadingRepository readingRepository, TrackerRepository trackerRepository) {
        this.readingRepository = readingRepository;
        this.trackerRepository = trackerRepository;
    }

    public Reading save(String trackerId, Instant recordedAt, BigDecimal temperature, Point position) {
        Reading reading = new Reading(trackerId, recordedAt, temperature, position);
        return readingRepository.save(reading);
    }

    public ReadingSeriesResponse query(String trackerId, Instant from, Instant to, int limit) {
        if (!trackerRepository.existsById(trackerId)) {
            throw new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId);
        }

        List<Reading> newestFirst = readingRepository.findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                trackerId, from, to, PageRequest.of(0, limit));

        List<ReadingPoint> chronological = new ArrayList<>(newestFirst.size());
        for (Reading reading : newestFirst) {
            chronological.add(toPoint(reading));
        }
        Collections.reverse(chronological);

        Instant nextBefore = newestFirst.size() == limit
                ? newestFirst.get(newestFirst.size() - 1).getRecordedAt()
                : null;

        return new ReadingSeriesResponse(trackerId, chronological, nextBefore);
    }

    private static ReadingPoint toPoint(Reading reading) {
        Point position = reading.getPosition();
        Double lat = position != null ? GeoPoints.lat(position) : null;
        Double lon = position != null ? GeoPoints.lon(position) : null;
        return new ReadingPoint(reading.getRecordedAt(), reading.getTemperature().doubleValue(), lat, lon);
    }
}
