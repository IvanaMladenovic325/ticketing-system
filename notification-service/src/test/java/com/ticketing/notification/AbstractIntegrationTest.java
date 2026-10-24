package com.ticketing.notification;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "management.tracing.export.zipkin.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-retries=1",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.default-requeue-rejected=false",
        "notification.mail.from=no-reply@ticketing.local"
})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

    protected static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    protected static final GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        postgres.start();
        rabbit.start();
        wireMock.start();
        greenMail.start();
    }

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"user-service", "event-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    wireMock::baseUrl);
        }
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }

}
