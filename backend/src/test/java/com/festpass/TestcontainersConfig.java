package com.festpass;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Real services for integration tests: PostgreSQL 16 (no H2, the booking guarantees depend on
 * PostgreSQL behaviour) and Mailpit to catch the emails. Import with @Import(TestcontainersConfig.class).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    public static final int MAILPIT_SMTP = 1025;
    public static final int MAILPIT_API = 8025;

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>("axllent/mailpit:v1.31")
                .withExposedPorts(MAILPIT_SMTP, MAILPIT_API)
                .waitingFor(Wait.forHttp("/livez").forPort(MAILPIT_API));
    }

    /** Points Spring Mail at the Mailpit container, and tells tests where its HTTP API is. */
    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(MAILPIT_SMTP));
            registry.add("test.mailpit.api",
                    () -> "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(MAILPIT_API));
        };
    }
}
