package com.fintechdev.ledger.service;

import com.fintechdev.ledger.dto.LedgerEntryRequest;
import com.fintechdev.ledger.model.LedgerEntry;
import com.fintechdev.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    
    private static final Logger logger = LoggerFactory.getLogger(LedgerService.class);
    private final LedgerEntryRepository repository;
    
    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    public LedgerEntry appendEntry(LedgerEntryRequest request) {
        // Check for duplicate transaction ID (idempotency)
        repository.findByTransactionId(request.getTransactionId())
            .ifPresent(existing -> {
                logger.info("Duplicate transaction ID detected, returning existing entry: {}", 
                    request.getTransactionId());
                throw new IllegalStateException("Transaction already exists: " + request.getTransactionId());
            });
        
        LedgerEntry entry = new LedgerEntry();
        entry.setTransactionId(request.getTransactionId());
        entry.setPaymentId(request.getPaymentId());
        entry.setAccountId(request.getAccountId());
        entry.setAmount(request.getAmount());
        entry.setCurrency(request.getCurrency());
        entry.setType(request.getType());
        
        LedgerEntry saved = repository.save(entry);
        logger.info("Ledger entry created: transactionId={}, accountId={}, amount={}, type={}", 
            saved.getTransactionId(), saved.getAccountId(), saved.getAmount(), saved.getType());
        
        return saved;
    }
}

