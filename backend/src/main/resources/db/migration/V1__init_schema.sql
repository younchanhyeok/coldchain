CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    company_name  VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('SHIPPER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tracker (
    id              VARCHAR(32)   PRIMARY KEY,
    shipper_id      BIGINT        NOT NULL REFERENCES app_user (id),
    product_name    VARCHAR(255)  NOT NULL,
    threshold_temp  NUMERIC(5, 2) NOT NULL,
    device_key_hash VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_tracker_shipper_id ON tracker (shipper_id);

CREATE TABLE tracker_latest (
    tracker_id    VARCHAR(32)   PRIMARY KEY REFERENCES tracker (id),
    last_ts       TIMESTAMPTZ,
    last_temp     NUMERIC(5, 2),
    last_position GEOMETRY(Point, 4326),
    version       BIGINT        NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE shipment (
    id                  BIGSERIAL     PRIMARY KEY,
    shipper_id          BIGINT        NOT NULL REFERENCES app_user (id),
    tracker_id          VARCHAR(32)   NOT NULL REFERENCES tracker (id),
    product_name        VARCHAR(255)  NOT NULL,
    origin_position     GEOMETRY(Point, 4326),
    origin_name         VARCHAR(255),
    destination_position GEOMETRY(Point, 4326),
    destination_name    VARCHAR(255),
    consignee_name      VARCHAR(255),
    consignee_contact   VARCHAR(50),
    driver_contact      VARCHAR(50),
    status              VARCHAR(20)   NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'IN_TRANSIT', 'DELIVERED')),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipment_shipper_id ON shipment (shipper_id);
-- 진행 중(READY/IN_TRANSIT)인 배송은 트래커당 1건만 허용 — DELIVERED 이후엔 같은 트래커를 다음 배송에 재사용(회수·재배치) 가능해야 함
CREATE UNIQUE INDEX uq_shipment_active_tracker ON shipment (tracker_id) WHERE status <> 'DELIVERED';

CREATE TABLE magic_link_token (
    token       VARCHAR(64) PRIMARY KEY,
    shipment_id BIGINT      NOT NULL REFERENCES shipment (id),
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- M6 TODO: TimescaleDB 전환 시 hypertable 파티셔닝 컬럼(recorded_at)이 PK에 포함되어야 함
-- → PK를 (id, recorded_at) 복합키로 ALTER. 지금은 단일 컬럼 PK로 두어 JPA @IdClass 보일러플레이트를 피함
-- (데이터가 적은 M6 시점에 ALTER 한 번으로 처리하는 편이 지금부터 복합키를 감당하는 것보다 총비용이 낮음)
CREATE TABLE reading (
    id           BIGSERIAL     PRIMARY KEY,
    tracker_id   VARCHAR(32)   NOT NULL REFERENCES tracker (id),
    recorded_at  TIMESTAMPTZ   NOT NULL,
    temperature  NUMERIC(5, 2) NOT NULL,
    position     GEOMETRY(Point, 4326),
    server_ts    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_reading_tracker_recorded_at ON reading (tracker_id, recorded_at DESC);

CREATE TABLE anomaly_event (
    id         BIGSERIAL     PRIMARY KEY,
    tracker_id VARCHAR(32)   NOT NULL REFERENCES tracker (id),
    ts         TIMESTAMPTZ   NOT NULL,
    type       VARCHAR(20)   NOT NULL CHECK (type IN ('SUDDEN', 'GRADUAL')),
    severity   VARCHAR(20)   NOT NULL,
    message    VARCHAR(500),
    z_score    NUMERIC(6, 3)
);
CREATE INDEX idx_anomaly_event_tracker_ts ON anomaly_event (tracker_id, ts DESC);

CREATE TABLE prediction (
    id                   BIGSERIAL     PRIMARY KEY,
    tracker_id           VARCHAR(32)   NOT NULL REFERENCES tracker (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    predicted_breach_at  TIMESTAMPTZ,
    lead_time_minutes    INTEGER,
    threshold_temp       NUMERIC(5, 2),
    slope_per_minute     NUMERIC(8, 4),
    model_version        VARCHAR(50),
    status               VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED', 'INVALIDATED', 'EXPIRED'))
);
CREATE INDEX idx_prediction_tracker_created_at ON prediction (tracker_id, created_at DESC);

CREATE TABLE alert (
    id          BIGSERIAL    PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    event_id    BIGINT       NOT NULL,
    channel     VARCHAR(20)  NOT NULL CHECK (channel IN ('SLACK', 'SSE', 'SMS')),
    status      VARCHAR(20)  NOT NULL,
    retry_count INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
