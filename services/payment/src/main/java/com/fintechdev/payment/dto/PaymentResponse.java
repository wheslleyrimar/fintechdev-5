package com.fintechdev.payment.dto;

import java.time.Instant;

public class PaymentResponse {
    private String paymentId;
    private String status;
    private Instant timestamp;
    
    // Default constructor for Jackson
    public PaymentResponse() {
    }
    
    public PaymentResponse(String paymentId, String status) {
        this.paymentId = paymentId;
        this.status = status;
        this.timestamp = Instant.now();
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

