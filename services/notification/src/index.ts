import * as amqp from 'amqplib';
import express from 'express';

interface PaymentCreatedEvent {
  event: string;
  paymentId: string;
  accountId: string;
  amount: number;
  currency: string;
  ts: number;
}

class NotificationService {
  private connection: any = null;
  private channel: any = null;
  private rabbitUrl: string;

  constructor() {
    this.rabbitUrl = process.env.RABBIT_URL || 'amqp://guest:guest@rabbitmq:5672/';
  }

  async connect(): Promise<void> {
    let retries = 0;
    const maxRetries = 10;

    while (retries < maxRetries) {
      try {
        const conn = await amqp.connect(this.rabbitUrl);
        this.connection = conn;
        
        const ch = await conn.createChannel();
        this.channel = ch;
        
        // Declare notification exchange
        await ch.assertExchange('notifications', 'topic', { durable: true });
        
        // Declare queue for payment notifications
        const queue = await ch.assertQueue('notifications.payment.created', { durable: true });
        
        // Bind queue to exchange
        await ch.bindQueue(queue.queue, 'notifications', 'payment.created');
        
        console.log('Connected to RabbitMQ and set up queues');
        return;
      } catch (error) {
        retries++;
        console.log(`Failed to connect to RabbitMQ, retrying... (${retries}/${maxRetries})`);
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
    }

    throw new Error('Failed to connect to RabbitMQ after max retries');
  }

  async startConsuming(): Promise<void> {
    if (!this.channel) {
      throw new Error('Channel not initialized');
    }

    await this.channel.consume('notifications.payment.created', async (msg) => {
      if (!msg) return;

      const startTime = Date.now();
      try {
        const event: PaymentCreatedEvent = JSON.parse(msg.content.toString());
        
        // Process notification (simulate async I/O operations)
        await this.sendNotification(event);
        
        this.channel!.ack(msg);
        
        const latency = Date.now() - startTime;
        console.log(JSON.stringify({
          service: 'notification',
          latency_ms: latency,
          paymentId: event.paymentId,
          event: 'processed'
        }));
      } catch (error) {
        console.error('Error processing notification:', error);
        this.channel!.nack(msg, false, true); // Requeue on error
      }
    }, { noAck: false });

    console.log('Notification service listening for payment events...');
  }

  private async sendNotification(event: PaymentCreatedEvent): Promise<void> {
    // Simulate async I/O operations (email, SMS, push, webhook)
    await new Promise(resolve => setTimeout(resolve, 100));
    
    // In a real implementation, this would:
    // - Send email via SMTP/SES
    // - Send SMS via Twilio
    // - Send push notification via FCM/APNS
    // - Trigger webhooks
    
    console.log(`Notification sent: Payment ${event.paymentId} of ${event.amount} ${event.currency} for account ${event.accountId}`);
  }

  async close(): Promise<void> {
    if (this.channel) await this.channel.close();
    if (this.connection) await this.connection.close();
  }
}

// Health check endpoint
const app = express();
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'notification' });
});

const PORT = process.env.PORT || 8082;
app.listen(PORT, () => {
  console.log(`Health check server listening on port ${PORT}`);
});

// Start notification service
const service = new NotificationService();
service.connect()
  .then(() => service.startConsuming())
  .catch((error) => {
    console.error('Failed to start notification service:', error);
    process.exit(1);
  });

// Graceful shutdown
process.on('SIGTERM', async () => {
  console.log('SIGTERM received, shutting down gracefully');
  await service.close();
  process.exit(0);
});

process.on('SIGINT', async () => {
  console.log('SIGINT received, shutting down gracefully');
  await service.close();
  process.exit(0);
});

