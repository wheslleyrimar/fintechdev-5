package com.fintechdev.ledger.repository;

import com.fintechdev.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Optional<LedgerEntry> findByTransactionId(String transactionId);
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);
}

