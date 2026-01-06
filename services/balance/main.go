package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type BalanceUpdate struct {
	PaymentID string  `json:"paymentId"`
	AccountID string  `json:"accountId"`
	Amount    float64 `json:"amount"`
	Operation string  `json:"operation"`
	Currency  string  `json:"currency"`
}

type CompensationEvent struct {
	PaymentID string  `json:"paymentId"`
	AccountID string  `json:"accountId"`
	Amount    float64 `json:"amount"`
	Currency  string  `json:"currency"`
}

type BalanceStore struct {
	mu       sync.RWMutex
	balances map[string]float64
	// Rastrear operações por paymentId para compensação
	operations map[string]BalanceUpdate
}

func NewBalanceStore() *BalanceStore {
	return &BalanceStore{
		balances:   make(map[string]float64),
		operations: make(map[string]BalanceUpdate),
	}
}

func (bs *BalanceStore) Update(accountID string, amount float64, operation string, paymentID string) {
	bs.mu.Lock()
	defer bs.mu.Unlock()

	current := bs.balances[accountID]
	if operation == "DEBIT" {
		bs.balances[accountID] = current - amount
	} else if operation == "CREDIT" {
		bs.balances[accountID] = current + amount
	}

	// Armazenar operação para possível compensação
	if paymentID != "" {
		bs.operations[paymentID] = BalanceUpdate{
			PaymentID: paymentID,
			AccountID: accountID,
			Amount:    amount,
			Operation: operation,
		}
	}

	log.Printf("Balance updated: accountId=%s, operation=%s, amount=%.2f, newBalance=%.2f",
		accountID, operation, amount, bs.balances[accountID])
}

func (bs *BalanceStore) Compensate(paymentID string) {
	bs.mu.Lock()
	defer bs.mu.Unlock()

	op, exists := bs.operations[paymentID]
	if !exists {
		log.Printf("No operation found for compensation: paymentId=%s", paymentID)
		return
	}

	current := bs.balances[op.AccountID]
	// Reverter a operação
	if op.Operation == "DEBIT" {
		bs.balances[op.AccountID] = current + op.Amount
	} else if op.Operation == "CREDIT" {
		bs.balances[op.AccountID] = current - op.Amount
	}

	// Remover operação após compensação
	delete(bs.operations, paymentID)

	log.Printf("Balance compensated: accountId=%s, paymentId=%s, newBalance=%.2f",
		op.AccountID, paymentID, bs.balances[op.AccountID])
}

func (bs *BalanceStore) Get(accountID string) float64 {
	bs.mu.RLock()
	defer bs.mu.RUnlock()
	return bs.balances[accountID]
}

func publishSagaEvent(ch *amqp.Channel, exchange, routingKey, event, paymentID, reason string) {
	eventData := map[string]interface{}{
		"event":     event,
		"paymentId": paymentID,
		"ts":        time.Now().UnixMilli(),
	}
	if reason != "" {
		eventData["reason"] = reason
	}

	body, err := json.Marshal(eventData)
	if err != nil {
		log.Printf("Failed to marshal event: %v", err)
		return
	}

	err = ch.Publish(exchange, routingKey, false, false, amqp.Publishing{
		ContentType: "application/json",
		Body:        body,
	})
	if err != nil {
		log.Printf("Failed to publish event: %v", err)
	} else {
		log.Printf("Published event: %s for paymentId: %s", event, paymentID)
	}
}

func main() {
	rabbitURL := os.Getenv("RABBIT_URL")
	if rabbitURL == "" {
		rabbitURL = "amqp://guest:guest@rabbitmq:5672/"
	}

	var conn *amqp.Connection
	var err error

	// Retry connection to RabbitMQ
	for i := 0; i < 10; i++ {
		conn, err = amqp.Dial(rabbitURL)
		if err == nil {
			break
		}
		log.Printf("Failed to connect to RabbitMQ, retrying... (%d/10)", i+1)
		time.Sleep(2 * time.Second)
	}
	if err != nil {
		log.Fatalf("Failed to connect to RabbitMQ: %v", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		log.Fatalf("Failed to open channel: %v", err)
	}
	defer ch.Close()

	// Declare exchanges
	err = ch.ExchangeDeclare("balance", "topic", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare balance exchange: %v", err)
	}

	err = ch.ExchangeDeclare("saga", "topic", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare saga exchange: %v", err)
	}

	// Declare queue for balance updates
	updateQueue, err := ch.QueueDeclare("balance.updates", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare update queue: %v", err)
	}

	// Declare queue for compensation
	compQueue, err := ch.QueueDeclare("balance.compensation", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare compensation queue: %v", err)
	}

	// Bind queues
	err = ch.QueueBind(updateQueue.Name, "update", "balance", false, nil)
	if err != nil {
		log.Fatalf("Failed to bind update queue: %v", err)
	}

	err = ch.QueueBind(compQueue.Name, "compensation", "balance", false, nil)
	if err != nil {
		log.Fatalf("Failed to bind compensation queue: %v", err)
	}

	balanceStore := NewBalanceStore()

	// Consumer para atualizações de saldo
	updateMsgs, err := ch.Consume(updateQueue.Name, "", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to register update consumer: %v", err)
	}

	// Consumer para compensação
	compMsgs, err := ch.Consume(compQueue.Name, "", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to register compensation consumer: %v", err)
	}

	log.Println("balance-service listening for balance updates and compensation...")

	// Processar atualizações de saldo
	go func() {
		for msg := range updateMsgs {
			start := time.Now()

			var event map[string]interface{}
			if err := json.Unmarshal(msg.Body, &event); err != nil {
				log.Printf("Failed to unmarshal balance update: %v", err)
				publishSagaEvent(ch, "saga", "balance.failed", "BalanceFailed", "", fmt.Sprintf("Invalid message: %v", err))
				continue
			}

			paymentID, _ := event["paymentId"].(string)
			accountID, _ := event["accountId"].(string)
			amount, _ := event["amount"].(string)
			_ = event["currency"] // Currency não é usado no balance service

			var amountFloat float64
			if _, err := fmt.Sscanf(amount, "%f", &amountFloat); err != nil {
				log.Printf("Failed to parse amount: %v", err)
				publishSagaEvent(ch, "saga", "balance.failed", "BalanceFailed", paymentID, fmt.Sprintf("Invalid amount: %v", err))
				continue
			}

			// Assumir DEBIT por padrão (pagamento diminui saldo)
			operation := "DEBIT"
			if op, ok := event["operation"].(string); ok {
				operation = op
			}

			balanceStore.Update(accountID, amountFloat, operation, paymentID)

			latency := time.Since(start).Microseconds()
			log.Printf(`{"service":"balance","latency_us":%d,"accountId":"%s","paymentId":"%s"}`, latency, accountID, paymentID)

			// Publicar evento de sucesso
			if paymentID != "" {
				publishSagaEvent(ch, "saga", "balance.completed", "BalanceCompleted", paymentID, "")
			}
		}
	}()

	// Processar compensações
	go func() {
		for msg := range compMsgs {
			var comp CompensationEvent
			if err := json.Unmarshal(msg.Body, &comp); err != nil {
				log.Printf("Failed to unmarshal compensation: %v", err)
				continue
			}

			balanceStore.Compensate(comp.PaymentID)
			log.Printf("Compensation processed for paymentId: %s", comp.PaymentID)

			// Publicar evento de compensação concluída
			publishSagaEvent(ch, "saga", "compensation.completed", "CompensationCompleted", comp.PaymentID, "")
		}
	}()

	// Manter o programa rodando
	select {}
}

