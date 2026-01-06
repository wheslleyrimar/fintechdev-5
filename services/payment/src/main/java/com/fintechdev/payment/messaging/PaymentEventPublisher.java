package com.fintechdev.payment.messaging;

import com.fintechdev.payment.dto.PaymentRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {
    
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${notification.exchange:notifications}")
    private String notificationExchange;
    
    @Value("${payments.exchange:payments}")
    private String paymentsExchange;
    
    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publishPaymentCreated(String paymentId, PaymentRequest request) {
        String message = String.format(
            "{\"event\":\"PaymentCreated\",\"paymentId\":\"%s\",\"accountId\":\"%s\",\"amount\":%s,\"currency\":\"%s\",\"ts\":%d}",
            paymentId, request.getAccountId(), request.getAmount(), request.getCurrency(), System.currentTimeMillis()
        );
        
        // Publish to notifications exchange (for notification service)
        rabbitTemplate.convertAndSend(notificationExchange, "payment.created", message);
        
        // Publish to payments exchange (for antifraud and other consumers)
        rabbitTemplate.convertAndSend(paymentsExchange, "", message);
    }
}

