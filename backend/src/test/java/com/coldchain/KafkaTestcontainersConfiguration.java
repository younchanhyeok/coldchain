package com.coldchain;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * kafka 모드(M6~ 기본) 수집 경로 검증용 — PG/Redis에 더해 KRaft Kafka 컨테이너를 띄우고
 * app.ingest.mode=kafka로 컨텍스트를 구성한다. 기존 TestcontainersConfiguration(direct 모드)과
 * 분리한 이유: mode가 달라 스프링 컨텍스트가 어차피 분리되고, direct 테스트들까지 브로커를
 * 기다리게 만들 필요가 없다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	public PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(
				DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	public GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

	// 3.8.1인 이유: Testcontainers 1.21의 KafkaContainer가 apache/kafka:3.9.0 기동에 실패한다
	// (RECOVERY→RUNNING 로그 대기 중 exit 1 — testcontainers-java#9506, not planned으로 닫힘).
	// 같은 3.9.0이 docker compose에서는 정상 기동하므로 infra는 3.9.0 유지 — 우리가 쓰는 기능
	// 범위(KRaft, 파티셔닝, DLT)에서 프로토콜 차이는 없다. Testcontainers 2.x 업그레이드 때 재시도.
	@Bean
	@ServiceConnection
	public KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
	}

	// 수집 모드 전환은 이 클래스가 아니라 테스트 클래스의 @TestPropertySource(app.ingest.mode=kafka)가
	// 담당한다 — @ConditionalOnProperty가 조건 평가 시점에 읽어야 해서 레지스트라로는 안 된다.
	@Bean
	public DynamicPropertyRegistrar kafkaTestProperties() {
		return registry -> registry.add("app.auth.jwt-secret",
				() -> "test-only-jwt-secret-at-least-32-bytes-long!!");
	}
}
