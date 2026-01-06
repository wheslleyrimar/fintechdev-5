package com.fintechdev.payment.service;

import com.fintechdev.payment.model.SagaState;
import com.fintechdev.payment.repository.SagaStateRepository;
import com.fintechdev.payment.messaging.PaymentEventPublisher;
import com.fintechdev.payment.dto.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class SagaOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(SagaOrchestrator.class);
    private final SagaStateRepository sagaRepository;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    public SagaOrchestrator(SagaStateRepository sagaRepository,
                           PaymentEventPublisher eventPublisher,
                           ObjectMapper objectMapper) {
        this.sagaRepository = sagaRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }
    
    @RabbitListener(queues = "saga.ledger.completed")
    public void handleLedgerCompleted(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            
            // Retry logic: aguardar até 2 segundos para a SAGA ser criada (race condition)
            Optional<SagaState> sagaOpt = Optional.empty();
            for (int attempt = 0; attempt < 10; attempt++) {
                sagaOpt = sagaRepository.findByPaymentId(paymentId);
                if (sagaOpt.isPresent()) {
                    break;
                }
                try {
                    Thread.sleep(200); // Aguardar 200ms entre tentativas
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (sagaOpt.isEmpty()) {
                logger.warn("Saga not found for paymentId: {} after retries", paymentId);
                return;
            }
            
            SagaState saga = sagaOpt.get();
            saga.setLedgerCompleted(true);
            checkAndCompleteSaga(saga);
            sagaRepository.save(saga);
            
            logger.info("Ledger completed for paymentId: {}", paymentId);
            
        } catch (Exception e) {
            logger.error("Error handling ledger completed", e);
        }
    }
    
    @RabbitListener(queues = "saga.balance.completed")
    public void handleBalanceCompleted(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            
            // Retry logic: aguardar até 2 segundos para a SAGA ser criada (race condition)
            Optional<SagaState> sagaOpt = Optional.empty();
            for (int attempt = 0; attempt < 10; attempt++) {
                sagaOpt = sagaRepository.findByPaymentId(paymentId);
                if (sagaOpt.isPresent()) {
                    break;
                }
                try {
                    Thread.sleep(200); // Aguardar 200ms entre tentativas
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            if (sagaOpt.isEmpty()) {
                logger.warn("Saga not found for paymentId: {} after retries", paymentId);
                return;
            }
            
            SagaState saga = sagaOpt.get();
            saga.setBalanceCompleted(true);
            checkAndCompleteSaga(saga);
            sagaRepository.save(saga);
            
            logger.info("Balance completed for paymentId: {}", paymentId);
            
        } catch (Exception e) {
            logger.error("Error handling balance completed", e);
        }
    }
    
    @RabbitListener(queues = "saga.ledger.failed")
    public void handleLedgerFailed(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            String reason = event.has("reason") ? event.get("reason").asText() : "Unknown error";
            
            Optional<SagaState> sagaOpt = sagaRepository.findByPaymentId(paymentId);
            if (sagaOpt.isEmpty()) {
                logger.warn("Saga not found for paymentId: {}", paymentId);
                return;
            }
            
            SagaState saga = sagaOpt.get();
            saga.setStatus(SagaState.SagaStatus.FAILED);
            saga.setFailureReason("Ledger failed: " + reason);
            sagaRepository.save(saga);
            
            // Iniciar compensação
            startCompensation(saga);
            
            logger.error("Ledger failed for paymentId: {}, reason: {}", paymentId, reason);
            
        } catch (Exception e) {
            logger.error("Error handling ledger failed", e);
        }
    }
    
    @RabbitListener(queues = "saga.balance.failed")
    public void handleBalanceFailed(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            String reason = event.has("reason") ? event.get("reason").asText() : "Unknown error";
            
            Optional<SagaState> sagaOpt = sagaRepository.findByPaymentId(paymentId);
            if (sagaOpt.isEmpty()) {
                logger.warn("Saga not found for paymentId: {}", paymentId);
                return;
            }
            
            SagaState saga = sagaOpt.get();
            saga.setStatus(SagaState.SagaStatus.FAILED);
            saga.setFailureReason("Balance failed: " + reason);
            sagaRepository.save(saga);
            
            // Iniciar compensação
            startCompensation(saga);
            
            logger.error("Balance failed for paymentId: {}, reason: {}", paymentId, reason);
            
        } catch (Exception e) {
            logger.error("Error handling balance failed", e);
        }
    }
    
    @RabbitListener(queues = "saga.compensation.completed")
    public void handleCompensationCompleted(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            
            Optional<SagaState> sagaOpt = sagaRepository.findByPaymentId(paymentId);
            if (sagaOpt.isEmpty()) {
                logger.warn("Saga not found for paymentId: {}", paymentId);
                return;
            }
            
            SagaState saga = sagaOpt.get();
            saga.setStatus(SagaState.SagaStatus.COMPENSATED);
            sagaRepository.save(saga);
            
            logger.info("Compensation completed for paymentId: {}", paymentId);
            
        } catch (Exception e) {
            logger.error("Error handling compensation completed", e);
        }
    }
    
    @Transactional
    private void checkAndCompleteSaga(SagaState saga) {
        // Não mudar status se já está em compensação ou compensado
        if (saga.getStatus() == SagaState.SagaStatus.COMPENSATING || 
            saga.getStatus() == SagaState.SagaStatus.COMPENSATED) {
            logger.debug("Saga is in compensation state, not changing to COMPLETED: paymentId={}, status={}", 
                saga.getPaymentId(), saga.getStatus());
            return;
        }
        
        if (Boolean.TRUE.equals(saga.getLedgerCompleted()) && 
            Boolean.TRUE.equals(saga.getBalanceCompleted())) {
            
            saga.setStatus(SagaState.SagaStatus.COMPLETED);
            logger.info("Saga completed successfully: paymentId={}", saga.getPaymentId());
        }
    }
    
    public void startCompensation(SagaState saga) {
        saga.setStatus(SagaState.SagaStatus.COMPENSATING);
        sagaRepository.save(saga);
        
        // Criar PaymentRequest para compensação
        PaymentRequest request = new PaymentRequest();
        request.setAccountId(saga.getAccountId());
        request.setAmount(new BigDecimal(saga.getAmount()));
        request.setCurrency(saga.getCurrency());
        
        // Compensar serviços que já completaram
        if (Boolean.TRUE.equals(saga.getLedgerCompleted())) {
            eventPublisher.publishCompensationRequest(
                saga.getPaymentId(), "ledger", request
            );
            logger.info("Compensation requested for ledger: paymentId={}", saga.getPaymentId());
        }
        
        if (Boolean.TRUE.equals(saga.getBalanceCompleted())) {
            eventPublisher.publishCompensationRequest(
                saga.getPaymentId(), "balance", request
            );
            logger.info("Compensation requested for balance: paymentId={}", saga.getPaymentId());
        }
    }
}

