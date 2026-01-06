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
    
    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange("saga", true, false);
    }
    
    // Queues for SAGA orchestration
    @Bean
    public Queue sagaLedgerCompletedQueue() {
        return QueueBuilder.durable("saga.ledger.completed").build();
    }
    
    @Bean
    public Queue sagaLedgerFailedQueue() {
        return QueueBuilder.durable("saga.ledger.failed").build();
    }
    
    @Bean
    public Queue sagaBalanceCompletedQueue() {
        return QueueBuilder.durable("saga.balance.completed").build();
    }
    
    @Bean
    public Queue sagaBalanceFailedQueue() {
        return QueueBuilder.durable("saga.balance.failed").build();
    }
    
    @Bean
    public Queue sagaCompensationCompletedQueue() {
        return QueueBuilder.durable("saga.compensation.completed").build();
    }
    
    // Bindings for SAGA queues
    @Bean
    public Binding sagaLedgerCompletedBinding() {
        return BindingBuilder.bind(sagaLedgerCompletedQueue())
            .to(sagaExchange())
            .with("ledger.completed");
    }
    
    @Bean
    public Binding sagaLedgerFailedBinding() {
        return BindingBuilder.bind(sagaLedgerFailedQueue())
            .to(sagaExchange())
            .with("ledger.failed");
    }
    
    @Bean
    public Binding sagaBalanceCompletedBinding() {
        return BindingBuilder.bind(sagaBalanceCompletedQueue())
            .to(sagaExchange())
            .with("balance.completed");
    }
    
    @Bean
    public Binding sagaBalanceFailedBinding() {
        return BindingBuilder.bind(sagaBalanceFailedQueue())
            .to(sagaExchange())
            .with("balance.failed");
    }
    
    @Bean
    public Binding sagaCompensationCompletedBinding() {
        return BindingBuilder.bind(sagaCompensationCompletedQueue())
            .to(sagaExchange())
            .with("compensation.completed");
    }
}

