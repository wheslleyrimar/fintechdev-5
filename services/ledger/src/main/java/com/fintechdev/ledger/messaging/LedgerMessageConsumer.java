package com.fintechdev.ledger.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechdev.ledger.dto.LedgerEntryRequest;
import com.fintechdev.ledger.model.LedgerEntry;
import com.fintechdev.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LedgerMessageConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(LedgerMessageConsumer.class);
    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;
    
    public LedgerMessageConsumer(LedgerService ledgerService, ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }
    
    @RabbitListener(queues = "ledger.entry.append")
    public void handleLedgerEntry(String message) {
        try {
            logger.info("Received ledger entry request: {}", message);
            
            // Parse JSON message
            var jsonNode = objectMapper.readTree(message);
            LedgerEntryRequest request = new LedgerEntryRequest();
            request.setTransactionId(jsonNode.get("transactionId").asText());
            request.setPaymentId(jsonNode.get("paymentId").asText());
            request.setAccountId(jsonNode.get("accountId").asText());
            request.setAmount(jsonNode.get("amount").decimalValue());
            request.setCurrency(jsonNode.get("currency").asText());
            request.setType(LedgerEntry.EntryType.valueOf(jsonNode.get("type").asText()));
            
            LedgerEntry entry = ledgerService.appendEntry(request);
            logger.info("Ledger entry processed successfully: transactionId={}", entry.getTransactionId());
            
        } catch (Exception e) {
            logger.error("Error processing ledger entry: {}", message, e);
            throw new RuntimeException("Failed to process ledger entry", e);
        }
    }
}

