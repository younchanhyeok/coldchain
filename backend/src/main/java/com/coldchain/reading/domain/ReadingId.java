package com.coldchain.reading.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * reading 복합키 (id, recorded_at) — hypertable 전환(V11)으로 파티션 컬럼이 PK에 포함되면서
 * 도입(M6 PR4). @IdClass 규약: no-arg 생성자 + equals/hashCode.
 */
public class ReadingId implements Serializable {

    private Long id;
    private Instant recordedAt;

    protected ReadingId() {
    }

    public ReadingId(Long id, Instant recordedAt) {
        this.id = id;
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadingId other)) {
            return false;
        }
        return Objects.equals(id, other.id) && Objects.equals(recordedAt, other.recordedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, recordedAt);
    }
}
