package com.coldchain.reading.service;

import com.coldchain.common.GeoPoints;
import com.coldchain.common.error.ResourceNotFoundException;
import com.coldchain.reading.domain.Reading;
import com.coldchain.reading.dto.NewReading;
import com.coldchain.reading.dto.ReadingPoint;
import com.coldchain.reading.dto.ReadingSeriesResponse;
import com.coldchain.reading.repository.ReadingBatchWriter;
import com.coldchain.reading.repository.ReadingDownsampleRepository;
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
    private final ReadingBatchWriter readingBatchWriter;
    private final ReadingDownsampleRepository downsampleRepository;
    private final TrackerRepository trackerRepository;

    public ReadingService(ReadingRepository readingRepository, ReadingBatchWriter readingBatchWriter,
            ReadingDownsampleRepository downsampleRepository, TrackerRepository trackerRepository) {
        this.readingRepository = readingRepository;
        this.readingBatchWriter = readingBatchWriter;
        this.downsampleRepository = downsampleRepository;
        this.trackerRepository = trackerRepository;
    }

    /** 단건 저장도 배치 writer 경로로 통일(M6 PR3) — ON CONFLICT 멱등성(재시도·재전달 흡수)을
     *  단건/배치/컨슈머가 같은 SQL로 공유한다. JPA save는 유니크 충돌 시 예외라 재시도에 취약. */
    public void save(String trackerId, Instant recordedAt, BigDecimal temperature, Point position) {
        Double lat = position != null ? GeoPoints.lat(position) : null;
        Double lon = position != null ? GeoPoints.lon(position) : null;
        readingBatchWriter.insertAll(List.of(new NewReading(trackerId, recordedAt, temperature, lat, lon)));
    }

    /** 배치 수집(M6) — JDBC 문장 배칭 한 방으로 insert. 단건 save 루프와 달리 왕복이 1회다. */
    public void saveBatch(List<NewReading> readings) {
        readingBatchWriter.insertAll(readings);
    }

    /** interval=null이면 원시 리딩, 1m/5m이면 다운샘플 CAgg 조회(M6 PR5). */
    public ReadingSeriesResponse query(String trackerId, Instant from, Instant to, int limit, ReadingInterval interval) {
        if (!trackerRepository.existsById(trackerId)) {
            throw new ResourceNotFoundException("트래커를 찾을 수 없습니다: " + trackerId);
        }

        List<ReadingPoint> newestFirst = interval != null
                ? downsampleRepository.query(interval, trackerId, from, to, limit)
                : rawPoints(trackerId, from, to, limit);

        // 두 경로 모두 최신순으로 limit개를 받아 시간순으로 뒤집는다 — nextBefore 페이지네이션 의미 동일.
        List<ReadingPoint> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);

        Instant nextBefore = newestFirst.size() == limit
                ? newestFirst.get(newestFirst.size() - 1).ts()
                : null;

        return new ReadingSeriesResponse(trackerId, chronological, nextBefore);
    }

    private List<ReadingPoint> rawPoints(String trackerId, Instant from, Instant to, int limit) {
        return readingRepository.findByTrackerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        trackerId, from, to, PageRequest.of(0, limit))
                .stream()
                .map(ReadingService::toPoint)
                .toList();
    }

    private static ReadingPoint toPoint(Reading reading) {
        Point position = reading.getPosition();
        Double lat = position != null ? GeoPoints.lat(position) : null;
        Double lon = position != null ? GeoPoints.lon(position) : null;
        return ReadingPoint.raw(reading.getRecordedAt(), reading.getTemperature().doubleValue(), lat, lon);
    }
}
