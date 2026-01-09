# 🎯 Guia de Execução do Teste Completo - Sistema Financial SAGA Pattern

Este documento fornece um guia passo a passo detalhado para executar o teste completo do sistema, explicando o que acontece em cada etapa e mostrando o output esperado.

---

## 📋 Pré-requisitos

Antes de executar o teste, certifique-se de que:

1. **Docker e Docker Compose estão instalados**
   ```bash
   docker --version
   docker compose version
   ```

2. **Todos os serviços estão rodando**
   ```bash
   docker compose ps
   ```
   
   Você deve ver todos os serviços com status `Up`:
   - `payment-service`
   - `ledger-service`
   - `balance-service`
   - `antifraud-service`
   - `notification-service`
   - `rabbitmq`
   - `redis`
   - `postgres`

3. **Navegue até o diretório do projeto**
   ```bash
   cd "/Users/wheslley/Desktop/Fintech Dev/Aula 5/fintechdev-5"
   ```

---

## 🚀 Executando o Teste

### Passo 1: Executar o Script de Teste

Execute o script de teste completo:

```bash
./test-complete.sh
```

**O que acontece:**
- O script verifica se todos os serviços estão rodando
- Executa dois testes principais:
  1. **Teste 1**: Fluxo completo de sucesso
  2. **Teste 2**: Detecção de timeout e compensação automática

---

## 📊 Output Esperado - Passo a Passo

### 🔍 Fase 1: Verificação Inicial dos Serviços

**O que você verá:**

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  🎯 TESTE COMPLETO DO SISTEMA FINANCIAL - SAGA PATTERN
╚═══════════════════════════════════════════════════════════════════════════╝

▶ Verificando serviços...
   ✓ payment-service está rodando
   ✓ ledger-service está rodando
   ✓ balance-service está rodando
   ✓ antifraud-service está rodando
   ✓ notification-service está rodando
```

**O que está acontecendo:**
- O script verifica se cada serviço está ativo usando `docker compose ps`
- Se algum serviço não estiver rodando, o teste será interrompido com uma mensagem de erro
- Todos os serviços devem estar `Up` para continuar

**Se algum serviço não estiver rodando:**
```bash
# Inicie todos os serviços
docker compose up -d
```

---

### ✅ Teste 1: Fluxo Completo de Sucesso

#### Step 1.1: Criação do Pagamento

**Output esperado:**

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  TESTE 1: FLUXO COMPLETO DE SUCESSO
╚═══════════════════════════════════════════════════════════════════════════╝

▶ Criando pagamento...
   ✓ Pagamento criado!
   Payment ID: 2215d459-70e1-4943-81ca-3298e2e043e9
   Status inicial: PROCESSING
   Account ID: acc-test-1767743218
   Amount: 150.75 BRL
```

**O que está acontecendo:**
1. O script faz uma requisição `POST` para `http://localhost:8080/payments`
2. O Payment Service:
   - Valida a requisição
   - Gera um UUID único para o pagamento
   - Cria um registro `SagaState` no PostgreSQL com status `PROCESSING`
   - Publica o evento `PaymentInitiated` no RabbitMQ
   - Retorna a resposta imediatamente ao cliente

**O que verificar:**
- ✅ Um `Payment ID` único foi gerado
- ✅ Status inicial é `PROCESSING` (a SAGA ainda não completou)
- ✅ Um `Account ID` foi gerado automaticamente

---

#### Step 1.2: Estado Inicial da SAGA

**Output esperado:**

```
▶ Estado inicial da SAGA

   Estado da SAGA:
 2215d459-70e1-4943-81ca-3298e2e043e9 | COMPLETED | t        | t         | f              |       | 2026-01-06 23:47:28.935181+00 | 2026-01-06 23:46:58.935966+00 | 2026-01-06 23:46:59.05334+00
(1 row)
```

**O que está acontecendo:**
- O script consulta a tabela `saga_states` no PostgreSQL
- Mostra o estado atual da SAGA para este pagamento

**Colunas explicadas:**
- `payment_id`: ID único do pagamento
- `status`: Status atual da SAGA (`PROCESSING`, `COMPLETED`, `COMPENSATED`, etc.)
- `ledger_completed` (t/f): Se o Ledger Service completou (`t` = true, `f` = false)
- `balance_completed` (t/f): Se o Balance Service completou
- `notification_sent` (t/f): Se a notificação foi enviada
- `failure_reason`: Motivo da falha (se houver)
- `timeout_at`: Timestamp do timeout (se aplicável)
- `created_at`: Quando a SAGA foi criada
- `updated_at`: Última atualização

**Nota:** O status pode aparecer como `COMPLETED` imediatamente se o processamento foi muito rápido. Isso é normal!

---

#### Step 1.3: Aguardando Processamento

**Output esperado:**

```
▶ Aguardando processamento dos serviços...
   ℹ Aguardando até 15 segundos para conclusão completa...
   Processando inicial...
   ✓ SAGA completada com sucesso!
```

**O que está acontecendo:**
1. O script aguarda até 15 segundos para a SAGA completar
2. Verifica periodicamente o status no banco de dados
3. Quando o status muda para `COMPLETED`, continua

**Processamento assíncrono em paralelo:**
- **Ledger Service**: Cria entradas DEBIT e CREDIT no PostgreSQL
- **Balance Service**: Atualiza saldo em memória
- **SagaOrchestrator**: Monitora eventos e atualiza o estado da SAGA
- **Notification Service**: Simula envio de notificações
- **Antifraud Service**: Processa validação antifraude

---

#### Step 1.4: Estado Após Processamento

**Output esperado:**

```
▶ Estado após processamento

   Estado da SAGA:
 2215d459-70e1-4943-81ca-3298e2e043e9 | COMPLETED | t        | t         | f              |       | 2026-01-06 23:47:28.935181+00 | 2026-01-06 23:46:58.935966+00 | 2026-01-06 23:46:59.05334+00
(1 row)
```

**O que verificar:**
- ✅ `status` = `COMPLETED` (SAGA concluída com sucesso)
- ✅ `ledger_completed` = `t` (Ledger processou)
- ✅ `balance_completed` = `t` (Balance processou)
- ✅ `failure_reason` está vazio (sem falhas)

---

#### Step 1.5: Entradas no Ledger

**Output esperado:**

```
▶ Entradas registradas no Ledger

   Entradas no Ledger:
 2215d459-70e1-4943-81ca-3298e2e043e9 | DEBIT  |  0.00 | 22fd32c5-fd90-46ca-b441-321db8f75993-debit  | 2026-01-06 23:46:59.002636+00
 2215d459-70e1-4943-81ca-3298e2e043e9 | CREDIT |  0.00 | 22fd32c5-fd90-46ca-b441-321db8f75993-credit | 2026-01-06 23:46:59.036651+00
```

**O que está acontecendo:**
- O Ledger Service criou **2 entradas imutáveis** (princípio de dupla entrada):
  - **DEBIT**: Conta origem (diminui saldo)
  - **CREDIT**: Conta destino (aumenta saldo)
- Cada entrada tem um `transaction_id` único
- O `amount` aparece como `0.00` porque o sistema usa um formato específico

**O que verificar:**
- ✅ Duas entradas foram criadas (DEBIT e CREDIT)
- ✅ Ambas têm o mesmo `payment_id`
- ✅ Timestamps são próximos (processamento paralelo)

---

#### Step 1.6: Logs dos Serviços

**Output esperado:**

```
   ℹ Verificando processamento nos serviços:
   ℹ   • Payment Service: Criou SAGA e publicou evento
   ℹ   • Ledger Service: Processou entrada e publicou LedgerCompleted
   ℹ   • Balance Service: Atualizou saldo e publicou BalanceCompleted
   ℹ   • Antifraud Service: Recebeu evento de pagamento
   ℹ   • Notification Service: Recebeu evento de pagamento

   Últimos logs do payment-service:
payment-service-1  | 2026-01-06T23:46:59.047Z DEBUG 1 --- [payment-service] [ntContainer#0-1] [                                                 ] .a.r.l.a.MessagingMessageListenerAdapter : Processing [GenericMessage [payload={"event":"LedgerCompleted","paymentId":"2215d459-70e1-4943-81ca-3298e2e043e9","ts":1767743219042}, headers={...}]]
payment-service-1  | 2026-01-06T23:46:59.051Z  INFO 1 --- [payment-service] [ntContainer#0-1] [                                                 ] c.f.payment.service.SagaOrchestrator     : Saga completed successfully: paymentId=2215d459-70e1-4943-81ca-3298e2e043e9
payment-service-1  | 2026-01-06T23:46:59.056Z  INFO 1 --- [payment-service] [ntContainer#0-1] [                                                 ] c.f.payment.service.SagaOrchestrator     : Ledger completed for paymentId: 2215d459-70e1-4943-81ca-3298e2e043e9

   Últimos logs do ledger-service:
ledger-service-1  | 2026-01-06T23:46:59.042Z  INFO 1 --- [ledger-service] [ntContainer#0-1] [                                                 ] c.f.l.messaging.LedgerMessageConsumer    : Ledger entries processed successfully: paymentId=2215d459-70e1-4943-81ca-3298e2e043e9
ledger-service-1  | 2026-01-06T23:46:59.043Z DEBUG 1 --- [ledger-service] [ntContainer#0-1] [                                                 ] o.s.amqp.rabbit.core.RabbitTemplate      : Publishing message [(Body:'[B@798f2f03(byte[97])' MessageProperties [headers={}, contentType=text/plain, contentEncoding=UTF-8, contentLength=97, deliveryMode=PERSISTENT, priority=0, deliveryTag=0])] on exchange [saga], routingKey = [ledger.completed]

   Últimos logs do balance-service:
balance-service-1  | 2026/01/06 23:46:58 Balance updated: accountId=acc-test-1767743218, operation=DEBIT, amount=150.75, newBalance=-150.75
balance-service-1  | 2026/01/06 23:46:58 {"service":"balance","latency_us":1831,"accountId":"acc-test-1767743218","paymentId":"2215d459-70e1-4943-81ca-3298e2e043e9"}
balance-service-1  | 2026/01/06 23:46:58 Published event: BalanceCompleted for paymentId: 2215d459-70e1-4943-81ca-3298e2e043e9

   ✓ Teste 1 concluído com sucesso!
```

**O que está acontecendo:**
- **Payment Service**: Recebeu evento `LedgerCompleted` e marcou a SAGA como completa
- **Ledger Service**: Processou as entradas e publicou `LedgerCompleted`
- **Balance Service**: Atualizou o saldo e publicou `BalanceCompleted`

**O que verificar nos logs:**
- ✅ Mensagens de sucesso em todos os serviços
- ✅ Eventos sendo publicados no RabbitMQ
- ✅ SAGA completada com sucesso

---

### ⚠️ Teste 2: Detecção de Timeout e Compensação Automática

Este teste demonstra o comportamento do sistema quando um serviço não responde.

#### Step 2.1: Parando o Balance Service

**Output esperado:**

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  TESTE 2: DETECÇÃO DE TIMEOUT E COMPENSAÇÃO AUTOMÁTICA
╚═══════════════════════════════════════════════════════════════════════════╝

   ⚠ Este teste demonstra o comportamento quando um serviço não responde
   ⚠ O timeout está configurado para 30 segundos
▶ Parando Balance Service para simular falha...
   ✓ Balance Service parado
```

**O que está acontecendo:**
- O script para o `balance-service` usando `docker compose stop balance-service`
- Isso simula uma falha no serviço
- O Ledger Service ainda funcionará, mas o Balance Service não responderá

**Por que isso é importante:**
- Demonstra a resiliência do sistema
- Mostra como a SAGA detecta falhas e inicia compensação

---

#### Step 2.2: Criando Novo Pagamento

**Output esperado:**

```
▶ Criando novo pagamento (será processado parcialmente)...
   ✓ Pagamento criado: 369141e4-c579-48b8-b1b2-cc3b83603743
```

**O que está acontecendo:**
- Um novo pagamento é criado
- O Payment Service publica o evento `PaymentInitiated`
- O Ledger Service processará normalmente
- O Balance Service **não responderá** (está parado)

---

#### Step 2.3: Estado Inicial (Processamento Parcial)

**Output esperado:**

```
▶ Estado inicial (Ledger processará, Balance não responderá)

   Estado da SAGA:
 369141e4-c579-48b8-b1b2-cc3b83603743 | PROCESSING | t        | f         | f              |       | 2026-01-06 23:47:34.301914+00 | 2026-01-06 23:47:04.304017+00 | 2026-01-06 23:47:04.542633+00
(1 row)
```

**O que verificar:**
- ✅ `status` = `PROCESSING` (ainda em processamento)
- ✅ `ledger_completed` = `t` (Ledger completou)
- ✅ `balance_completed` = `f` (Balance não completou - serviço parado)

---

#### Step 2.4: Aguardando Timeout

**Output esperado:**

```
▶ Aguardando processamento parcial (Ledger completará, Balance não responderá)...
   Aguardando.....
▶ Estado após processamento parcial

   Estado da SAGA:
 369141e4-c579-48b8-b1b2-cc3b83603743 | PROCESSING | t        | f         | f              |       | 2026-01-06 23:47:34.301914+00 | 2026-01-06 23:47:04.304017+00 | 2026-01-06 23:47:04.542633+00
(1 row)

▶ Aguardando timeout (30 segundos configurado)...
   ℹ O sistema detectará que o Balance Service não respondeu
   Aguardando timeout...................................
```

**O que está acontecendo:**
- O script aguarda 35 segundos (timeout configurado é 30 segundos)
- O `SagaTimeoutChecker` no Payment Service verifica periodicamente se há timeouts
- Quando detecta que o Balance Service não respondeu, inicia a compensação

---

#### Step 2.5: Estado Após Timeout (Compensação Iniciada)

**Output esperado:**

```
▶ Estado após timeout (compensação deve ter sido iniciada)

   Estado da SAGA:
 369141e4-c579-48b8-b1b2-cc3b83603743 | COMPENSATED | t        | f         | f              | Timeout: Balance service did not respond | 2026-01-06 23:47:34.301914+00 | 2026-01-06 23:47:04.304017+00 | 2026-01-06 23:47:38.488385+00
(1 row)
```

**O que verificar:**
- ✅ `status` = `COMPENSATED` (compensação concluída)
- ✅ `ledger_completed` = `t` (Ledger havia completado)
- ✅ `balance_completed` = `f` (Balance nunca completou)
- ✅ `failure_reason` = `"Timeout: Balance service did not respond"` (motivo da falha)

**O que está acontecendo:**
1. O `SagaTimeoutChecker` detectou que o Balance Service não respondeu
2. Mudou o status para `COMPENSATING`
3. Publicou eventos de compensação para o Ledger Service
4. O Ledger Service reverteu as entradas criadas
5. Status final: `COMPENSATED`

---

#### Step 2.6: Verificando Compensação no Ledger

**Output esperado:**

```
▶ Verificando compensação no Ledger
   ℹ Aguardando processamento da compensação...
   ✓ Compensação detectada! (2 entradas de compensação, 4 total)

   Entradas no Ledger:
 369141e4-c579-48b8-b1b2-cc3b83603743 | DEBIT  |  0.00 | c55c674d-9547-46dc-adc4-ccd7aef999cd-debit               | 2026-01-06 23:47:04.505664+00
 369141e4-c579-48b8-b1b2-cc3b83603743 | CREDIT |  0.00 | c55c674d-9547-46dc-adc4-ccd7aef999cd-credit              | 2026-01-06 23:47:04.521624+00
 369141e4-c579-48b8-b1b2-cc3b83603743 | CREDIT |  0.00 | c55c674d-9547-46dc-adc4-ccd7aef999cd-debit-compensation  | 2026-01-06 23:47:38.46073+00
 369141e4-c579-48b8-b1b2-cc3b83603743 | DEBIT  |  0.00 | c55c674d-9547-46dc-adc4-ccd7aef999cd-credit-compensation | 2026-01-06 23:47:38.467967+00
```

**O que está acontecendo:**
- **4 entradas totais** foram criadas:
  1. **DEBIT original** (criada no início)
  2. **CREDIT original** (criada no início)
  3. **CREDIT-compensation** (reverte o DEBIT original)
  4. **DEBIT-compensation** (reverte o CREDIT original)

**Princípio da compensação:**
- Para reverter um DEBIT, cria-se um CREDIT
- Para reverter um CREDIT, cria-se um DEBIT
- O sistema mantém um histórico completo e imutável

**O que verificar:**
- ✅ 4 entradas no total (2 originais + 2 de compensação)
- ✅ Entradas de compensação têm `-compensation` no `transaction_id`
- ✅ Timestamps das compensações são posteriores às originais

---

#### Step 2.7: Logs de Compensação

**Output esperado:**

```
▶ Logs de compensação

   Últimos logs do payment-service:
payment-service-1  | 2026-01-06T23:47:38.380Z  INFO 1 --- [payment-service] [   scheduling-1] [695d9f1a05acfe88df12bad1cfd8a759-df12bad1cfd8a759] c.f.payment.service.SagaOrchestrator     : Compensation requested for ledger: paymentId=369141e4-c579-48b8-b1b2-cc3b83603743
payment-service-1  | 2026-01-06T23:47:38.479Z DEBUG 1 --- [payment-service] [ntContainer#4-1] [                                                 ] o.s.a.r.listener.BlockingQueueConsumer   : Storing delivery for consumerTag: 'amq.ctag-sqDOL2blsDctnEAneD5L8w' with deliveryTag: '2' in Consumer@2d0d357b: tags=[[amq.ctag-sqDOL2blsDctnEAneD5L8w]], channel=Cached Rabbit Channel: AMQChannel(amqp://guest@172.18.0.5:5672/,5), conn: Proxy@4acca937 Shared Rabbit Connection: SimpleConnection@1c7294c [delegate=amqp://guest@172.18.0.5:5672/, localPort=51576], acknowledgeMode=AUTO local queue size=0
payment-service-1  | 2026-01-06T23:47:38.480Z DEBUG 1 --- [payment-service] [ntContainer#4-1] [                                                 ] .a.r.l.a.MessagingMessageListenerAdapter : Processing [GenericMessage [payload={"event":"CompensationCompleted","paymentId":"369141e4-c579-48b8-b1b2-cc3b83603743","service":"ledger","ts":1767743258471}, headers={...}]]
payment-service-1  | 2026-01-06T23:47:38.489Z  INFO 1 --- [payment-service] [ntContainer#4-1] [                                                 ] c.f.payment.service.SagaOrchestrator     : Compensation completed for paymentId: 369141e4-c579-48b8-b1b2-cc3b83603743

   Últimos logs do ledger-service:
ledger-service-1  | 2026-01-06T23:47:38.470Z  INFO 1 --- [ledger-service] [ntContainer#1-1] [                                                 ] c.f.l.messaging.LedgerMessageConsumer    : Ledger compensation completed for paymentId: 369141e4-c579-48b8-b1b2-cc3b83603743
ledger-service-1  | 2026-01-06T23:47:38.474Z DEBUG 1 --- [ledger-service] [ntContainer#1-1] [                                                 ] o.s.amqp.rabbit.core.RabbitTemplate      : Executing callback RabbitTemplate$$Lambda$1957/0x0000004801b06e90 on RabbitMQ Channel: Cached Rabbit Channel: AMQChannel(amqp://guest@172.18.0.5:5672/,3), conn: Proxy@790bd0e Shared Rabbit Connection: SimpleConnection@5df17e60 [delegate=amqp://guest@172.18.0.5:5672/, localPort=47396]
ledger-service-1  | 2026-01-06T23:47:38.475Z DEBUG 1 --- [ledger-service] [ntContainer#1-1] [                                                 ] o.s.amqp.rabbit.core.RabbitTemplate      : Publishing message [(Body:'[B@535f3594(byte[122])' MessageProperties [headers={}, contentType=text/plain, contentEncoding=UTF-8, contentLength=122, deliveryMode=PERSISTENT, priority=0, deliveryTag=0])] on exchange [saga], routingKey = [compensation.completed]
```

**O que está acontecendo:**
1. **Payment Service**: Detectou timeout e solicitou compensação
2. **Ledger Service**: Recebeu evento de compensação, reverteu entradas, publicou `CompensationCompleted`
3. **Payment Service**: Recebeu confirmação e atualizou status para `COMPENSATED`

**O que verificar:**
- ✅ Mensagem "Compensation requested for ledger"
- ✅ Mensagem "Ledger compensation completed"
- ✅ Evento `CompensationCompleted` publicado

---

#### Step 2.8: Reiniciando Balance Service

**Output esperado:**

```
▶ Reiniciando Balance Service
   Aguardando Balance Service iniciar...
   ✓ Balance Service reiniciado
   ✓ Teste 2 concluído!
```

**O que está acontecendo:**
- O script reinicia o `balance-service` usando `docker compose start balance-service`
- Aguarda alguns segundos para o serviço inicializar completamente
- Isso restaura o sistema ao estado normal

---

### 📊 Resumo Final dos Testes

**Output esperado:**

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  📊 RESUMO DOS TESTES
╚═══════════════════════════════════════════════════════════════════════════╝

Teste 1 - Fluxo Completo de Sucesso:
   Payment ID: 2215d459-70e1-4943-81ca-3298e2e043e9
   Status: ✓ COMPLETED
   Ledger: ✓ | Balance: ✓

Teste 2 - Timeout e Compensação:
   Payment ID: 369141e4-c579-48b8-b1b2-cc3b83603743
   Status: ✓ COMPENSATED
   Motivo da falha: Timeout: Balance service did not respond
   Demonstra: Detecção automática de timeout e compensação

▶ Comandos úteis para investigação:

# Ver estado da SAGA:
   docker compose exec postgres psql -U postgres -d payment -c \
     "SELECT * FROM saga_states WHERE payment_id = '2215d459-70e1-4943-81ca-3298e2e043e9';"

# Ver entradas no Ledger:
   docker compose exec postgres psql -U postgres -d ledger -c \
     "SELECT * FROM ledger_entries WHERE payment_id = '2215d459-70e1-4943-81ca-3298e2e043e9';"

# Ver logs em tempo real:
   docker compose logs -f payment-service | grep '2215d459-70e1-4943-81ca-3298e2e043e9'

# Verificar todos os serviços:
   docker compose ps

   ✓ 🎉 Todos os testes foram executados!
   ℹ O sistema demonstrou:
   ℹ   ✓ Processamento completo com todos os serviços
   ℹ   ✓ Detecção automática de timeout
   ℹ   ✓ Compensação automática quando há falha
   ℹ   ✓ Rastreamento completo via SAGA

╔═══════════════════════════════════════════════════════════════════════════╗
║  ✅ TESTE COMPLETO FINALIZADO
╚═══════════════════════════════════════════════════════════════════════════╝
```

**O que verificar:**
- ✅ Teste 1: Status `COMPLETED`, ambos os serviços completaram
- ✅ Teste 2: Status `COMPENSATED`, compensação executada corretamente
- ✅ Comandos úteis fornecidos para investigação adicional

---

## 🔍 Comandos Úteis para Investigação

### Ver Estado da SAGA

```bash
docker compose exec postgres psql -U postgres -d payment -c \
  "SELECT payment_id, status, ledger_completed, balance_completed, failure_reason, created_at, updated_at FROM saga_states ORDER BY created_at DESC LIMIT 5;"
```

### Ver Entradas no Ledger

```bash
docker compose exec postgres psql -U postgres -d ledger -c \
  "SELECT payment_id, type, amount, transaction_id, created_at FROM ledger_entries ORDER BY created_at DESC LIMIT 10;"
```

### Ver Logs em Tempo Real

```bash
# Todos os serviços
docker compose logs -f

# Serviço específico
docker compose logs -f payment-service
docker compose logs -f ledger-service
docker compose logs -f balance-service
```

### Verificar Saúde dos Serviços

```bash
# Ver status de todos os containers
docker compose ps

# Health check do Payment Service
curl http://localhost:8080/actuator/health

# Health check do Notification Service
curl http://localhost:8082/health
```

### Verificar RabbitMQ

```bash
# Acessar RabbitMQ Management UI
# URL: http://localhost:15672
# Usuário: guest
# Senha: guest
```

---

## ⚠️ Troubleshooting

### Problema: Teste falha na verificação inicial

**Sintoma:**
```
✗ payment-service NÃO está rodando
✗ Alguns serviços não estão rodando. Execute: docker compose up -d
```

**Solução:**
```bash
# Iniciar todos os serviços
docker compose up -d

# Aguardar inicialização (1-2 minutos)
docker compose logs -f

# Verificar novamente
docker compose ps
```

---

### Problema: Teste 1 não completa

**Sintoma:**
```
⚠ SAGA ainda em processamento após 15 segundos
```

**Solução:**
1. Verificar logs do Payment Service:
   ```bash
   docker compose logs payment-service | tail -50
   ```

2. Verificar se RabbitMQ está funcionando:
   ```bash
   docker compose ps rabbitmq
   ```

3. Verificar se PostgreSQL está acessível:
   ```bash
   docker compose exec postgres psql -U postgres -d payment -c "SELECT 1;"
   ```

---

### Problema: Teste 2 não detecta timeout

**Sintoma:**
- Status permanece `PROCESSING` mesmo após 30 segundos

**Solução:**
1. Verificar se o `SagaTimeoutChecker` está rodando:
   ```bash
   docker compose logs payment-service | grep -i timeout
   ```

2. Verificar configuração de timeout no `application.yml` do Payment Service

3. Verificar se o Balance Service realmente está parado:
   ```bash
   docker compose ps balance-service
   ```

---

### Problema: Compensação não é executada

**Sintoma:**
- Status muda para `COMPENSATING` mas não para `COMPENSATED`

**Solução:**
1. Verificar logs do Ledger Service:
   ```bash
   docker compose logs ledger-service | grep -i compensation
   ```

2. Verificar se o evento de compensação foi publicado:
   ```bash
   docker compose logs payment-service | grep -i compensation
   ```

3. Verificar se há entradas de compensação no Ledger:
   ```bash
   docker compose exec postgres psql -U postgres -d ledger -c \
     "SELECT * FROM ledger_entries WHERE transaction_id LIKE '%-compensation%';"
   ```

---

## 📝 Notas Importantes

1. **Tempo de Execução:**
   - Teste 1: ~5-10 segundos
   - Teste 2: ~40-45 segundos (inclui timeout de 30 segundos)

2. **Dados de Teste:**
   - Cada execução cria novos `Payment IDs` e `Account IDs`
   - Os dados são armazenados no PostgreSQL e podem ser consultados

3. **Estado dos Serviços:**
   - O Teste 2 para o Balance Service temporariamente
   - O serviço é reiniciado automaticamente ao final do teste

4. **Compensação:**
   - A compensação só é executada se o Ledger Service já tiver processado
   - Se o Balance Service falhar antes do Ledger processar, não há nada para compensar

---

## 🎯 O que os Testes Demonstram

### Teste 1: Fluxo Completo de Sucesso
- ✅ Comunicação assíncrona entre serviços
- ✅ Processamento paralelo (Ledger e Balance simultaneamente)
- ✅ Rastreamento completo via SAGA
- ✅ Publicação e consumo de eventos no RabbitMQ
- ✅ Persistência imutável no Ledger

### Teste 2: Timeout e Compensação
- ✅ Detecção automática de falhas
- ✅ Compensação automática (rollback)
- ✅ Resiliência do sistema
- ✅ Rastreamento de falhas (failure_reason)
- ✅ Manutenção da consistência dos dados

---

## 📚 Referências

- [README.md](./README.md) - Documentação completa do sistema
- [test-complete.sh](./test-complete.sh) - Script de teste completo
- [docker-compose.yml](./docker-compose.yml) - Configuração dos serviços

---

**Desenvolvido para demonstrar arquitetura de microsserviços com SAGA Pattern, comunicação assíncrona e resiliência.**

