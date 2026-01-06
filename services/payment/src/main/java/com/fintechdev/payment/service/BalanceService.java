package com.fintechdev.payment.service;

import com.fintechdev.payment.dto.PaymentRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {
    
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${balance.exchange:balance}")
    private String balanceExchange;
    
    public BalanceService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void updateBalance(String accountId, java.math.BigDecimal amount) {
        // Publish balance update event
        String message = String.format(
            "{\"accountId\":\"%s\",\"amount\":%s,\"operation\":\"DEBIT\"}",
            accountId, amount
        );
        
        rabbitTemplate.convertAndSend(balanceExchange, "update", message);
    }
}

