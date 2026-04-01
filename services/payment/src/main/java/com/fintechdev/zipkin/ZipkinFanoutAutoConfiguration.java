package com.fintechdev.zipkin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.tracing.zipkin.ZipkinAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Roda antes do {@link ZipkinAutoConfiguration} (fora do scan de {@code com.fintechdev.payment}).
 * Define o {@link Sender} usado pelo Brave; {@code @Primary} evita ambiguidade se o Boot criar outro {@code URLConnectionSender}.
 */
@AutoConfiguration
@AutoConfigureBefore(ZipkinAutoConfiguration.class)
public class ZipkinFanoutAutoConfiguration {

    @Bean
    @Primary
    Sender zipkinSender(
            @Value("${management.tracing.export.zipkin.endpoint}") String primary,
            @Value("${management.tracing.export.zipkin.secondary-endpoint:}") String secondary) {
        URLConnectionSender a = URLConnectionSender.create(primary);
        if (secondary == null || secondary.isBlank()) {
            return a;
        }
        return new FanoutZipkinSender(a, URLConnectionSender.create(secondary));
    }
}
