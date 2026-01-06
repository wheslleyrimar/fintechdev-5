package main

import (
  "log"
  "os"
  "time"

  amqp "github.com/rabbitmq/amqp091-go"
)

func main() {
  rabbitURL := os.Getenv("RABBIT_URL")
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
  
  err = ch.ExchangeDeclare("payments", "fanout", true, false, false, false, nil)
  if err != nil {
    log.Fatalf("Failed to declare exchange: %v", err)
  }

  q, err := ch.QueueDeclare("", false, true, true, false, nil)
  if err != nil {
    log.Fatalf("Failed to declare queue: %v", err)
  }
  
  err = ch.QueueBind(q.Name, "", "payments", false, nil)
  if err != nil {
    log.Fatalf("Failed to bind queue: %v", err)
  }

  msgs, err := ch.Consume(q.Name, "", true, false, false, false, nil)
  if err != nil {
    log.Fatalf("Failed to register consumer: %v", err)
  }

  for m := range msgs {
    time.Sleep(200 * time.Millisecond)
    log.Printf(`{"service":"%s","event":"processed","body":"%s"}`, os.Getenv("SERVICE_NAME"), string(m.Body))
  }
}
