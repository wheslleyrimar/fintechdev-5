package com.fintechdev.ledger.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintechdev.ledger.dto.LedgerEntryRequest;
import com.fintechdev.ledger.model.LedgerEntry;
import com.fintechdev.ledger.service.LedgerService;
import com.fintechdev.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerMessageConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(LedgerMessageConsumer.class);
    private final LedgerService ledgerService;
    private final LedgerEntryRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${saga.exchange:saga}")
    private String sagaExchange;
    
    public LedgerMessageConsumer(LedgerService ledgerService,
                                LedgerEntryRepository repository,
                                ObjectMapper objectMapper,
                                RabbitTemplate rabbitTemplate) {
        this.ledgerService = ledgerService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @RabbitListener(queues = "ledger.entry.append")
    public void handleLedgerEntry(String message) {
        String paymentId = null;
        try {
            logger.info("Received ledger entry request: {}", message);
            
            // Parse JSON message
            var jsonNode = objectMapper.readTree(message);
            paymentId = jsonNode.get("paymentId").asText();
            
            // Criar entradas DEBIT e CREDIT (double-entry bookkeeping)
            String transactionId = UUID.randomUUID().toString();
            
            // DEBIT - Conta origem
            LedgerEntryRequest debitRequest = new LedgerEntryRequest();
            debitRequest.setTransactionId(transactionId + "-debit");
            debitRequest.setPaymentId(paymentId);
            debitRequest.setAccountId(jsonNode.get("accountId").asText());
            debitRequest.setAmount(jsonNode.get("amount").decimalValue());
            debitRequest.setCurrency(jsonNode.get("currency").asText());
            debitRequest.setType(LedgerEntry.EntryType.DEBIT);
            
            ledgerService.appendEntry(debitRequest);
            
            // CREDIT - Conta destino (assumindo uma conta padrão para demonstração)
            // Em produção, isso viria do request
            LedgerEntryRequest creditRequest = new LedgerEntryRequest();
            creditRequest.setTransactionId(transactionId + "-credit");
            creditRequest.setPaymentId(paymentId);
            creditRequest.setAccountId("system-account"); // Conta destino
            creditRequest.setAmount(jsonNode.get("amount").decimalValue());
            creditRequest.setCurrency(jsonNode.get("currency").asText());
            creditRequest.setType(LedgerEntry.EntryType.CREDIT);
            
            ledgerService.appendEntry(creditRequest);
            
            logger.info("Ledger entries processed successfully: paymentId={}", paymentId);
            
            // Publicar evento de sucesso
            publishLedgerCompleted(paymentId);
            
        } catch (Exception e) {
            logger.error("Error processing ledger entry: {}", message, e);
            
            // Publicar evento de falha
            if (paymentId != null) {
                publishLedgerFailed(paymentId, e.getMessage());
            }
            
            throw new RuntimeException("Failed to process ledger entry", e);
        }
    }
    
    @RabbitListener(queues = "ledger.compensation")
    public void handleCompensation(String message) {
        try {
            logger.info("Received compensation request: {}", message);
            
            var event = objectMapper.readTree(message);
            String paymentId = event.get("paymentId").asText();
            
            // Buscar todas as entradas do ledger para este paymentId
            List<LedgerEntry> entries = repository.findByPaymentId(paymentId);
            
            if (entries.isEmpty()) {
                logger.warn("No ledger entries found for compensation: paymentId={}", paymentId);
                return;
            }
            
            // Criar entradas de compensação (reverter DEBIT/CREDIT)
            for (LedgerEntry entry : entries) {
                // Pular entradas de compensação já existentes
                if (entry.getTransactionId().contains("-compensation")) {
                    continue;
                }
                
                LedgerEntryRequest compensationRequest = new LedgerEntryRequest();
                compensationRequest.setTransactionId(entry.getTransactionId() + "-compensation");
                compensationRequest.setPaymentId(entry.getPaymentId());
                compensationRequest.setAccountId(entry.getAccountId());
                compensationRequest.setAmount(entry.getAmount());
                compensationRequest.setCurrency(entry.getCurrency());
                // Reverter: se era DEBIT, vira CREDIT e vice-versa
                compensationRequest.setType(
                    entry.getType() == LedgerEntry.EntryType.DEBIT 
                        ? LedgerEntry.EntryType.CREDIT 
                        : LedgerEntry.EntryType.DEBIT
                );
                
                ledgerService.appendEntry(compensationRequest);
            }
            
            logger.info("Ledger compensation completed for paymentId: {}", paymentId);
            
            // Publicar evento de compensação concluída
            publishCompensationCompleted(paymentId);
            
        } catch (Exception e) {
            logger.error("Error compensating ledger entry", e);
        }
    }
    
    private void publishLedgerCompleted(String paymentId) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("event", "LedgerCompleted");
            event.put("paymentId", paymentId);
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(sagaExchange, "ledger.completed", message);
            
        } catch (Exception e) {
            logger.error("Failed to publish ledger completed event", e);
        }
    }
    
    private void publishLedgerFailed(String paymentId, String reason) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("event", "LedgerFailed");
            event.put("paymentId", paymentId);
            event.put("reason", reason);
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(sagaExchange, "ledger.failed", message);
            
        } catch (Exception e) {
            logger.error("Failed to publish ledger failed event", e);
        }
    }
    
    private void publishCompensationCompleted(String paymentId) {
        try {
            ObjectNode event = objectMapper.createObjectNode();
            event.put("event", "CompensationCompleted");
            event.put("paymentId", paymentId);
            event.put("service", "ledger");
            event.put("ts", System.currentTimeMillis());
            
            String message = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(sagaExchange, "compensation.completed", message);
            
        } catch (Exception e) {
            logger.error("Failed to publish compensation completed event", e);
        }
    }
}

