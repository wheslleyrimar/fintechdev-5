package com.fintechdev.payment.messaging;

import com.fintechdev.payment.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentEventPublisher {
    
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${ledger.exchange:ledger}")
    private String ledgerExchange;
    
    @Value("${balance.exchange:balance}")
    private String balanceExchange;
    
    @Value("${notification.exchange:notifications}")
    private String notificationExchange;
    
    @Value("${payments.exchange:payments}")
    private String paymentsExchange;
    
    @Value("${saga.exchange:saga}")
    private String sagaExchange;
    
    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void publishPaymentInitiated(String paymentId, PaymentRequest request) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "PaymentInitiated");
            event.put("paymentId", paymentId);
            event.put("accountId", request.getAccountId());
            event.put("amount", request.getAmount().toString());
            event.put("currency", request.getCurrency());
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            
            // Publica evento de início da SAGA
            rabbitTemplate.convertAndSend(sagaExchange, "payment.initiated", message);
            
            // Publica para serviços que precisam processar
            rabbitTemplate.convertAndSend(ledgerExchange, "entry.append", message);
            rabbitTemplate.convertAndSend(balanceExchange, "update", message);
            rabbitTemplate.convertAndSend(notificationExchange, "payment.created", message);
            rabbitTemplate.convertAndSend(paymentsExchange, "", message);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish payment initiated event", e);
        }
    }
    
    public void publishPaymentCreated(String paymentId, PaymentRequest request) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "PaymentCreated");
            event.put("paymentId", paymentId);
            event.put("accountId", request.getAccountId());
            event.put("amount", request.getAmount().toString());
            event.put("currency", request.getCurrency());
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            
            // Publish to notifications exchange (for notification service)
            rabbitTemplate.convertAndSend(notificationExchange, "payment.created", message);
            
            // Publish to payments exchange (for antifraud and other consumers)
            rabbitTemplate.convertAndSend(paymentsExchange, "", message);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish payment created event", e);
        }
    }
    
    public void publishCompensationRequest(String paymentId, String service, PaymentRequest request) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("event", "CompensationRequested");
            event.put("paymentId", paymentId);
            event.put("service", service);
            event.put("accountId", request.getAccountId());
            event.put("amount", request.getAmount().toString());
            event.put("currency", request.getCurrency());
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            
            // Publica no exchange específico do serviço
            if ("ledger".equals(service)) {
                rabbitTemplate.convertAndSend(ledgerExchange, "compensation", message);
            } else if ("balance".equals(service)) {
                rabbitTemplate.convertAndSend(balanceExchange, "compensation", message);
            }
            
            // Também publica no exchange de SAGA
            rabbitTemplate.convertAndSend(sagaExchange, "compensation.requested", message);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish compensation request", e);
        }
    }
}

