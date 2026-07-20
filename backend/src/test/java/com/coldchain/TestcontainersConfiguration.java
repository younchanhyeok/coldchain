package com.coldchain;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// TimescaleDB + PostGIS(M6 PR4) — 프로덕션 compose와 같은 이미지로 hypertable 마이그레이션까지
	// 통합 테스트가 실제로 검증한다. 첫 pull이 크므로(수 GB) CI 캐시가 없으면 느릴 수 있다.
	// withCommand로 timescaledb preload를 걸지 않는다 — ha 이미지의 initdb 스크립트
	// (001_timescaledb_tune.sh)가 postgresql.conf에 이미 기록하고, command를 덮어쓰면
	// PostgreSQLContainer 기본 command의 fsync=off(테스트 속도)까지 사라진다(리뷰에서 실측).
	@Bean
	@ServiceConnection
	public PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(
				DockerImageName.parse("timescale/timescaledb-ha:pg16").asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	public GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

	// JwtTokenProvider가 시크릿 미설정 시 기동을 거부(fail-fast)하므로, 이 설정을 import하는
	// 모든 통합 테스트에 테스트 전용 시크릿을 일괄 주입한다(테스트마다 properties 반복 방지).
	// (수집 모드는 여기가 아니라 test resources의 application.properties에서 direct로 고정한다 —
	// @ConditionalOnProperty가 조건 평가 시점에 읽어야 해서 레지스트라로는 안 된다.)
	@Bean
	public DynamicPropertyRegistrar authTestProperties() {
		return registry -> registry.add("app.auth.jwt-secret",
				() -> "test-only-jwt-secret-at-least-32-bytes-long!!");
	}

}
