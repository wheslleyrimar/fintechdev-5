package com.fintechdev.payment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_states", indexes = {
    @Index(name = "idx_payment_id", columnList = "paymentId"),
    @Index(name = "idx_status", columnList = "status")
})
public class SagaState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String paymentId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStatus status;
    
    @Column(nullable = false)
    private String accountId;
    
    @Column(nullable = false)
    private String amount;
    
    @Column(nullable = false)
    private String currency;
    
    private Boolean ledgerCompleted;
    private Boolean balanceCompleted;
    private Boolean notificationSent;
    
    @Column(length = 500)
    private String failureReason;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    private Instant updatedAt;
    
    @Column(name = "timeout_at")
    private Instant timeoutAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = SagaStatus.PENDING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public enum SagaStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        COMPENSATING,
        COMPENSATED
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    public SagaStatus getStatus() {
        return status;
    }
    
    public void setStatus(SagaStatus status) {
        this.status = status;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getAmount() {
        return amount;
    }
    
    public void setAmount(String amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public Boolean getLedgerCompleted() {
        return ledgerCompleted;
    }
    
    public void setLedgerCompleted(Boolean ledgerCompleted) {
        this.ledgerCompleted = ledgerCompleted;
    }
    
    public Boolean getBalanceCompleted() {
        return balanceCompleted;
    }
    
    public void setBalanceCompleted(Boolean balanceCompleted) {
        this.balanceCompleted = balanceCompleted;
    }
    
    public Boolean getNotificationSent() {
        return notificationSent;
    }
    
    public void setNotificationSent(Boolean notificationSent) {
        this.notificationSent = notificationSent;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Instant getTimeoutAt() {
        return timeoutAt;
    }
    
    public void setTimeoutAt(Instant timeoutAt) {
        this.timeoutAt = timeoutAt;
    }
}

