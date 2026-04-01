package com.fintechdev.ledger.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Evita traces do scrape Prometheus em {@code /actuator/prometheus}, que dominam a lista no Zipkin.
 */
@Configuration
public class TracingNoiseReductionConfig {

    @Bean
    ObservationPredicate reduceTracingNoisePredicate() {
        return (name, context) -> {
            if ("http.server.requests".equals(name) && context instanceof ServerRequestObservationContext serverCtx) {
                var req = serverCtx.getCarrier();
                if (req != null) {
                    String path = req.getRequestURI();
                    if (path != null && path.startsWith("/actuator/")) {
                        return false;
                    }
                }
            }
            return true;
        };
    }
}
