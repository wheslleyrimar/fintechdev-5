package com.fintechdev.payment.service;

import com.fintechdev.payment.dto.PaymentRequest;
import com.fintechdev.payment.dto.PaymentResponse;
import com.fintechdev.payment.messaging.PaymentEventPublisher;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final BalanceService balanceService;
    private final PaymentEventPublisher eventPublisher;
    
    public PaymentService(
            IdempotencyService idempotencyService,
            LedgerService ledgerService,
            BalanceService balanceService,
            PaymentEventPublisher eventPublisher) {
        this.idempotencyService = idempotencyService;
        this.ledgerService = ledgerService;
        this.balanceService = balanceService;
        this.eventPublisher = eventPublisher;
    }
    
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackProcessPayment")
    @Retry(name = "paymentService")
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Check idempotency
            if (idempotencyKey != null) {
                PaymentResponse existing = idempotencyService.getExistingResponse(idempotencyKey);
                if (existing != null) {
                    logger.info("Idempotent request detected, returning existing payment: {}", existing.getPaymentId());
                    return existing;
                }
            }
            
            String paymentId = UUID.randomUUID().toString();
            
            // Append to ledger (debit/credit)
            String transactionId = ledgerService.appendEntry(paymentId, request);
            
            // Update balance projection
            balanceService.updateBalance(request.getAccountId(), request.getAmount());
            
            // Publish notification event
            eventPublisher.publishPaymentCreated(paymentId, request);
            
            PaymentResponse response = new PaymentResponse(paymentId, "PROCESSED");
            
            // Store for idempotency
            if (idempotencyKey != null) {
                idempotencyService.storeResponse(idempotencyKey, response);
            }
            
            long latency = System.currentTimeMillis() - startTime;
            logger.info("Payment processed successfully: paymentId={}, latency_ms={}", paymentId, latency);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error processing payment", e);
            throw new RuntimeException("Failed to process payment", e);
        }
    }
    
    public PaymentResponse fallbackProcessPayment(PaymentRequest request, String idempotencyKey, Exception ex) {
        logger.warn("Circuit breaker opened, using fallback for payment processing");
        throw new RuntimeException("Payment service temporarily unavailable", ex);
    }
}

