package com.coldchain.reading.repository;

import com.coldchain.reading.dto.ReadingPoint;
import com.coldchain.reading.service.ReadingInterval;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 다운샘플 조회(M6 PR5) — continuous aggregate 뷰(reading_1m/5m)를 읽는다. CAgg는 JPA 엔티티가
 * 아니라 JdbcTemplate로 접근. 실시간 집계(materialized_only=false)라 아직 굳지 않은 최신 버킷도
 * 이 쿼리 한 방에 즉석 계산돼 함께 나온다.
 */
@Repository
public class ReadingDownsampleRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReadingDownsampleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * [from, to) 버킷을 최신순 limit개 조회. 뷰 이름은 enum 화이트리스트라 인젝션 안전.
     * ST_Y/ST_X로 geometry에서 lat/lon을 뽑는다(버킷 내 마지막 위치, 없으면 null).
     */
    public List<ReadingPoint> query(ReadingInterval interval, String trackerId, Instant from, Instant to, int limit) {
        String sql = """
                SELECT bucket, avg_temp, min_temp, max_temp,
                       ST_Y(last_position) AS lat, ST_X(last_position) AS lon
                  FROM %s
                 WHERE tracker_id = ? AND bucket >= ? AND bucket < ?
                 ORDER BY bucket DESC
                 LIMIT ?
                """.formatted(interval.viewName());

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ReadingPoint(
                        rs.getTimestamp("bucket").toInstant(),
                        rs.getBigDecimal("avg_temp").doubleValue(),
                        rs.getBigDecimal("min_temp").doubleValue(),
                        rs.getBigDecimal("max_temp").doubleValue(),
                        (Double) rs.getObject("lat"),
                        (Double) rs.getObject("lon")),
                trackerId, Timestamp.from(from), Timestamp.from(to), limit);
    }
}
