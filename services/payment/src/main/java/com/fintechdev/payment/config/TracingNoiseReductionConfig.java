package com.fintechdev.payment.config;

import com.fintechdev.payment.service.SagaTimeoutChecker;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

/**
 * Reduz ruído no Zipkin: scrapes do Prometheus e o scheduler da SAGA geram muitos traces
 * e escondem {@code POST /payments} na UI com limite baixo.
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
            if (context instanceof ScheduledTaskObservationContext sched) {
                if (sched.getTargetClass() == SagaTimeoutChecker.class) {
                    return false;
                }
            }
            return true;
        };
    }
}
