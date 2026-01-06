package com.fintechdev.payment.service;

import com.fintechdev.payment.dto.PaymentRequest;
import com.fintechdev.payment.dto.PaymentResponse;
import com.fintechdev.payment.messaging.PaymentEventPublisher;
import com.fintechdev.payment.model.SagaState;
import com.fintechdev.payment.repository.SagaStateRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private final IdempotencyService idempotencyService;
    private final PaymentEventPublisher eventPublisher;
    private final SagaStateRepository sagaRepository;
    private final long sagaTimeoutSeconds;
    
    public PaymentService(
            IdempotencyService idempotencyService,
            PaymentEventPublisher eventPublisher,
            SagaStateRepository sagaRepository,
            @Value("${saga.timeout.seconds:30}") long sagaTimeoutSeconds) {
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.sagaRepository = sagaRepository;
        this.sagaTimeoutSeconds = sagaTimeoutSeconds;
    }
    
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackProcessPayment")
    @Retry(name = "paymentService")
    @Transactional
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
            
            // Criar estado inicial da SAGA
            SagaState saga = new SagaState();
            saga.setPaymentId(paymentId);
            saga.setStatus(SagaState.SagaStatus.PROCESSING);
            saga.setAccountId(request.getAccountId());
            saga.setAmount(request.getAmount().toString());
            saga.setCurrency(request.getCurrency());
            saga.setLedgerCompleted(false);
            saga.setBalanceCompleted(false);
            saga.setNotificationSent(false);
            // Definir timeout: agora + timeout configurado
            saga.setTimeoutAt(Instant.now().plusSeconds(sagaTimeoutSeconds));
            sagaRepository.save(saga);
            
            // Publicar evento de início da SAGA (dispara processamento assíncrono)
            eventPublisher.publishPaymentInitiated(paymentId, request);
            
            PaymentResponse response = new PaymentResponse(paymentId, "PROCESSING");
            
            // Store for idempotency
            if (idempotencyKey != null) {
                idempotencyService.storeResponse(idempotencyKey, response);
            }
            
            long latency = System.currentTimeMillis() - startTime;
            logger.info("Payment initiated with SAGA: paymentId={}, latency_ms={}", paymentId, latency);
            
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

