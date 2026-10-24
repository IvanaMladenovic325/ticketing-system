package com.ticketing.booking.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConfigTest {

    private final ObservationPredicate predicate = new TracingConfig().skipTechnicalRequests();

    @Test
    void actuatorRequestsAreNotObserved() {
        assertThat(predicate.test("http.server.requests", serverContext("/actuator/health"))).isFalse();
        assertThat(predicate.test("http.server.requests", serverContext("/actuator/prometheus"))).isFalse();
    }

    @Test
    void businessRequestsAreObserved() {
        assertThat(predicate.test("http.server.requests", serverContext("/api/bookings"))).isTrue();
    }

    @Test
    void eurekaHeartbeatIsNotObserved() {
        assertThat(predicate.test("http.client.requests",
                clientContext("http://discovery-server:8761/eureka/apps/BOOKING-SERVICE/id"))).isFalse();
    }

    @Test
    void outgoingBusinessCallsAreObserved() {
        assertThat(predicate.test("http.client.requests",
                clientContext("http://user-service/api/users/1"))).isTrue();
    }

    @Test
    void otherObservationsAreNotAffected() {
        assertThat(predicate.test("spring.rabbit.template", new Observation.Context())).isTrue();
    }

    private static ServerRequestObservationContext serverContext(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
    }

    private static ClientRequestObservationContext clientContext(String uri) {
        return new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.GET, URI.create(uri)));
    }

}
