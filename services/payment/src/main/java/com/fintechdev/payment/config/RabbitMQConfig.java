package com.fintechdev.payment.config;

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
    public TopicExchange balanceExchange() {
        return new TopicExchange("balance", true, false);
    }
    
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("notifications", true, false);
    }
    
    @Bean
    public FanoutExchange paymentsExchange() {
        return new FanoutExchange("payments", true, false);
    }
}

