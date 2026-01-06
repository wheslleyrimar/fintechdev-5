package com.fintechdev.ledger.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    @Bean
    public TopicExchange ledgerExchange() {
        return new TopicExchange("ledger", true, false);
    }
    
    @Bean
    public Queue ledgerEntryQueue() {
        return QueueBuilder.durable("ledger.entry.append").build();
    }
    
    @Bean
    public Binding ledgerEntryBinding() {
        return BindingBuilder
            .bind(ledgerEntryQueue())
            .to(ledgerExchange())
            .with("entry.append");
    }
}

