package com.fintechdev.payment.service;

import com.fintechdev.payment.dto.PaymentRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {
    
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${ledger.exchange:ledger}")
    private String ledgerExchange;
    
    public LedgerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public String appendEntry(String paymentId, PaymentRequest request) {
        String transactionId = java.util.UUID.randomUUID().toString();
        
        // Publish ledger entry event
        String message = String.format(
            "{\"transactionId\":\"%s\",\"paymentId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"currency\":\"%s\",\"type\":\"DEBIT\"}",
            transactionId, paymentId, request.getAccountId(), request.getAmount(), request.getCurrency()
        );
        
        rabbitTemplate.convertAndSend(ledgerExchange, "entry.append", message);
        
        return transactionId;
    }
}

