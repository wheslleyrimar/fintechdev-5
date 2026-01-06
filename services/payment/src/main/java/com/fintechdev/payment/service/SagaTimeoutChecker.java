package com.fintechdev.payment.service;

import com.fintechdev.payment.model.SagaState;
import com.fintechdev.payment.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class SagaTimeoutChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(SagaTimeoutChecker.class);
    private final SagaStateRepository sagaRepository;
    private final SagaOrchestrator sagaOrchestrator;
    
    public SagaTimeoutChecker(SagaStateRepository sagaRepository, SagaOrchestrator sagaOrchestrator) {
        this.sagaRepository = sagaRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }
    
    /**
     * Verifica SAGAs que excederam o timeout a cada 5 segundos
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void checkTimeouts() {
        Instant now = Instant.now();
        
        // Buscar SAGAs em PROCESSING que excederam o timeout
        List<SagaState> timedOutSagas = sagaRepository.findTimedOutSagas(
            SagaState.SagaStatus.PROCESSING, 
            now
        ).stream()
            .filter(saga -> !Boolean.TRUE.equals(saga.getLedgerCompleted()) || !Boolean.TRUE.equals(saga.getBalanceCompleted()))
            .toList();
        
        for (SagaState saga : timedOutSagas) {
            logger.warn("SAGA timeout detected: paymentId={}, timeoutAt={}, ledgerCompleted={}, balanceCompleted={}",
                saga.getPaymentId(), saga.getTimeoutAt(), saga.getLedgerCompleted(), saga.getBalanceCompleted());
            
            // Determinar qual serviço falhou
            String failureReason;
            if (!Boolean.TRUE.equals(saga.getLedgerCompleted()) && !Boolean.TRUE.equals(saga.getBalanceCompleted())) {
                failureReason = "Timeout: Both Ledger and Balance services did not respond";
            } else if (!Boolean.TRUE.equals(saga.getLedgerCompleted())) {
                failureReason = "Timeout: Ledger service did not respond";
            } else {
                failureReason = "Timeout: Balance service did not respond";
            }
            
            saga.setStatus(SagaState.SagaStatus.FAILED);
            saga.setFailureReason(failureReason);
            sagaRepository.save(saga);
            
            // Iniciar compensação
            sagaOrchestrator.startCompensation(saga);
        }
    }
}

