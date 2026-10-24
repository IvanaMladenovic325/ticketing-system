package com.ticketing.apigateway.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class TracingConfig {

    /**
     * Ne prate se tehnički zahtevi bez poslovnog značaja, da Zipkin ne bi bio zatrpan:
     * dolazni zahtevi ka /actuator (Docker healthcheck, Prometheus scrape)
     * i odlazni pozivi ka Eureki (registracija i heartbeat na svakih 30 s).
     */
    @Bean
    ObservationPredicate skipTechnicalRequests() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext server && server.getCarrier() != null) {
                return !server.getCarrier().getRequestURI().startsWith("/actuator");
            }
            if (context instanceof ClientRequestObservationContext client && client.getCarrier() != null) {
                return !client.getCarrier().getURI().getPath().startsWith("/eureka/");
            }
            return true;
        };
    }

}
