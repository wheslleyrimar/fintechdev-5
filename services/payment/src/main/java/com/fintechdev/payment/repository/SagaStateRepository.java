package com.fintechdev.payment.repository;

import com.fintechdev.payment.model.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, java.util.UUID> {
    Optional<SagaState> findByPaymentId(String paymentId);
    
    @Query("SELECT s FROM SagaState s WHERE s.status = :status AND s.timeoutAt IS NOT NULL AND s.timeoutAt < :now")
    List<SagaState> findTimedOutSagas(SagaState.SagaStatus status, Instant now);
}

