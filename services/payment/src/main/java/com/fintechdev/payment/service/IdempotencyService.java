package com.fintechdev.payment.service;

import com.fintechdev.payment.dto.PaymentResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long TTL_HOURS = 24;
    
    public IdempotencyService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    public PaymentResponse getExistingResponse(String idempotencyKey) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        return (PaymentResponse) redisTemplate.opsForValue().get(key);
    }
    
    public void storeResponse(String idempotencyKey, PaymentResponse response) {
        String key = IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(key, response, TTL_HOURS, TimeUnit.HOURS);
    }
}

