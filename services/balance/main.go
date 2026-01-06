package main

import (
	"encoding/json"
	"log"
	"os"
	"sync"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type BalanceUpdate struct {
	AccountID string  `json:"accountId"`
	Amount    float64 `json:"amount"`
	Operation string  `json:"operation"`
}

type BalanceStore struct {
	mu       sync.RWMutex
	balances map[string]float64
}

func NewBalanceStore() *BalanceStore {
	return &BalanceStore{
		balances: make(map[string]float64),
	}
}

func (bs *BalanceStore) Update(accountID string, amount float64, operation string) {
	bs.mu.Lock()
	defer bs.mu.Unlock()

	current := bs.balances[accountID]
	if operation == "DEBIT" {
		bs.balances[accountID] = current - amount
	} else if operation == "CREDIT" {
		bs.balances[accountID] = current + amount
	}

	log.Printf("Balance updated: accountId=%s, operation=%s, amount=%.2f, newBalance=%.2f",
		accountID, operation, amount, bs.balances[accountID])
}

func (bs *BalanceStore) Get(accountID string) float64 {
	bs.mu.RLock()
	defer bs.mu.RUnlock()
	return bs.balances[accountID]
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

	// Declare balance exchange
	err = ch.ExchangeDeclare("balance", "topic", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare exchange: %v", err)
	}

	// Declare queue for balance updates
	q, err := ch.QueueDeclare("balance.updates", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to declare queue: %v", err)
	}

	// Bind queue to exchange
	err = ch.QueueBind(q.Name, "update", "balance", false, nil)
	if err != nil {
		log.Fatalf("Failed to bind queue: %v", err)
	}

	msgs, err := ch.Consume(q.Name, "", true, false, false, false, nil)
	if err != nil {
		log.Fatalf("Failed to register consumer: %v", err)
	}

	balanceStore := NewBalanceStore()

	log.Println("balance-service listening for balance updates...")

	for msg := range msgs {
		start := time.Now()

		var update BalanceUpdate
		if err := json.Unmarshal(msg.Body, &update); err != nil {
			log.Printf("Failed to unmarshal balance update: %v", err)
			continue
		}

		balanceStore.Update(update.AccountID, update.Amount, update.Operation)

		latency := time.Since(start).Microseconds()
		log.Printf(`{"service":"balance","latency_us":%d,"accountId":"%s"}`, latency, update.AccountID)
	}
}

