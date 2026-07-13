package com.coldchain.reading.repository;

import com.coldchain.reading.dto.NewReading;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 원시 리딩 JDBC 배치 insert(M6) — JPA save 루프는 IDENTITY 채번 때문에 문장 배칭이
 * 원리적으로 불가능해(insert마다 왕복) 배치 경로만 JdbcTemplate로 내린다. baseline
 * 부하테스트에서 리딩당 DB 왕복이 커넥션 풀 경쟁의 주범이었다.
 */
@Repository
public class ReadingBatchWriter {

    // ST_MakePoint는 (lon, lat) 순서 — GeoJSON과 같고 API의 lat/lon 표기와 반대라 헷갈리기 쉽다.
    private static final String INSERT_SQL = """
            INSERT INTO reading (tracker_id, recorded_at, temperature, position, server_ts)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ReadingBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(String trackerId, List<NewReading> readings) {
        if (readings.isEmpty()) {
            return;
        }
        Timestamp serverTs = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(INSERT_SQL, readings, readings.size(), (ps, reading) -> {
            ps.setString(1, trackerId);
            ps.setTimestamp(2, Timestamp.from(reading.recordedAt()));
            ps.setBigDecimal(3, reading.temperature());
            ps.setObject(4, reading.lon(), Types.DOUBLE);
            ps.setObject(5, reading.lat(), Types.DOUBLE);
            ps.setTimestamp(6, serverTs);
        });
    }
}
