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

	@Bean
	@ServiceConnection
	public PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	public GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

	// JwtTokenProvider가 시크릿 미설정 시 기동을 거부(fail-fast)하므로, 이 설정을 import하는
	// 모든 통합 테스트에 테스트 전용 시크릿을 일괄 주입한다(테스트마다 properties 반복 방지).
	@Bean
	public DynamicPropertyRegistrar authTestProperties() {
		return registry -> registry.add("app.auth.jwt-secret",
				() -> "test-only-jwt-secret-at-least-32-bytes-long!!");
	}

}
