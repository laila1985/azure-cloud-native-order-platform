# Guide — Building the Azure Cloud Native Order Platform

This guide walks through building `azure-cloud-native-order-platform` step by
step. It is the Azure equivalent of the AWS project
(`aws-cloud-native-order-platform`), replacing AWS services with Azure services
while keeping the same Spring Boot application shape and REST API.

## Service mapping

| AWS (original)                  | Azure (this project)                       |
|---------------------------------|--------------------------------------------|
| DynamoDB (Enhanced Client)      | Azure SQL Database (Spring Data JPA)       |
| SNS topic → 5 SQS queues        | Service Bus **Topic → 5 Subscriptions**    |
| SES                             | Azure Communication Services **Email**     |
| SNS SMS                         | Azure Communication Services **SMS**       |
| ElastiCache (Redis)             | Azure Cache for Redis                      |
| CloudFormation security stack   | **Bicep**                                  |
| IAM roles                       | Managed Identity + RBAC role assignments   |
| Secrets Manager + KMS           | Key Vault                                  |
| DynamoDB Local + LocalStack     | Azure SQL Edge + Service Bus emulator      |

---

## Step 1 — Spring Boot + REST API + Azure SQL

### Domain model

Instead of DynamoDB's `@DynamoDbBean` annotations, the entities use Jakarta
Persistence (JPA):

- `model/Customer.java` — `@Entity` mapped to the `customer` table, primary key
  `customerId`.
- `model/Order.java` — `@Entity` mapped to `orders`, primary key `orderId`, with
  a `@OneToMany` (cascade + orphanRemoval) of `OrderItem`s.
- `model/OrderItem.java` — `@Entity` mapped to `order_item`, generated identity
  `itemId`, plus `productId`, `productName`, `quantity`, `unitPrice`.

### Repositories

`repository/OrderRepository` and `repository/CustomerRepository` extend Spring
Data `JpaRepository`. Instead of DynamoDB's `getItem`/`scan`, they use derived
queries (`findByOrderId`, `findByCustomerId`) and `findAll()`.

### Services

`service/OrderService` mirrors the AWS version: `create` enforces referential
integrity (the order's customer must exist), auto-generates `orderId`,
`createdAt`, and `status`, computes `totalAmount`, persists, then publishes the
creation event. `findById` uses the cache-aside pattern; `update` enforces that
`customerId` is immutable; `updateStatus` is used by the async consumers.

### Controllers

`controller/CustomerController` and `controller/OrderController` expose the same
REST endpoints as the AWS project (`/api/customers`, `/api/orders`).

### Configuration

`application.yml` defaults to an **in-memory H2** datasource (MSSQL
compatibility mode) so the app runs with no Docker. `application-azure.yml`
switches to Azure SQL (or Azure SQL Edge locally).

---

## Step 2 — Service Bus topic + subscriptions (the fan-out)

The AWS `SnsClient` + `SqsClient` become Service Bus clients.

### `config/ServiceBusConfig`

Builds a `ServiceBusSenderClient` for the topic and five `ServiceBusReceiverClient`
beans — one per subscription — using the connection string.

### `messaging/MessagingResources`

On startup, idempotently creates the topic and five subscriptions via the
`ServiceBusAdministrationClient` (analog of the AWS `MessagingResources`).

### `messaging/publisher/OrderEventPublisher`

Publishes the `OrderCreatedEvent` as JSON via `sender.sendMessage(...)`.
Best-effort: failures are logged and swallowed so order creation never fails.

---

## Step 3 — Asynchronous consumers

`messaging/consumer/AbstractOrderConsumer` is a shared `@Scheduled` poller that
receives a batch of messages (PEEK_LOCK), marks the order status, and completes
(or abandons) each message.

- `OrderProcessor` — subscription `order-processor`, marks `PROCESSED`.
- `OrderLambdaHandler` — subscription `order-lambda`, marks `NOTIFIED`. This
  stands in for a real **Azure Function**; in production, replace it with a

---

## Step 4 — Email and SMS notifications (Azure Communication Services)

`messaging/notification/` contains the email and SMS pipelines:

- `EmailNotificationHandler` / `SmsNotificationHandler` — `@Scheduled` pollers
  on the `order-email` / `order-sms` subscriptions.
- `NotificationValidator` — validates the channel-specific required fields.
- `TemplateService` — loads and renders `{{placeholder}}` templates.
- `EmailProvider` — sends via `EmailClient` (ACS Email).
- `SmsProvider` — sends via `SmsClient` (ACS SMS).

When no ACS connection string is configured (local development), the providers
no-op and log instead of sending.

---

## Step 5 — Security (Bicep)

`src/main/resources/bicep/security.bicep` is the Azure equivalent of the AWS
CloudFormation security stack:

- **Key Vault** (with RBAC authorization + soft delete) — analog of Secrets
  Manager + KMS.
- **User-assigned Managed Identities** for the app and the Function consumer —
  analog of IAM roles.
- **RBAC role assignments** — Key Vault Secrets User, Service Bus Data
  Owner/Receiver — analog of IAM policies.
- Outputs expose the Key Vault URI and identity client IDs.

Deploy with:

```bash
az group create -n rg-order-platform -l westeurope
az deployment group create -g rg-order-platform -f security.bicep \
  -p applicationName=order-platform stage=dev
```

---

## Step 6 — Redis caching (cache-aside)

`config/RedisCacheConfig` is identical in spirit to the AWS version: it configures
a `RedisCacheManager` with a Jackson serializer that understands `java.time`
types. `OrderService.findById` is `@Cacheable`, `update`/`updateStatus` are
`@CachePut`, and `delete` is `@CacheEvict`. The Redis instance is Azure Cache for
Redis in production (a plain `redis` container locally).

The config is gated behind `app.cache.redis.enabled` (default `true`) so tests
can disable it and run without Redis.

---

## Local development

`docker-compose.yml` starts:

- **Azure SQL Edge** — SQL Server-compatible database (port 1433).
- **Service Bus emulator** — topic + subscriptions (port 5672), backed by the
  SQL Edge container.
- **Redis** (port 6379).

Run with the `azure` profile to target SQL Edge:

```bash
docker compose up -d
gradlew.bat bootRun --args='--spring.profiles.active=azure'
```

Or run with zero dependencies (H2 + no-op messaging):

```bash
gradlew.bat bootRun
```

  Function triggered by the topic.
- `ShippingProcessor` — subscription `order-shipping`, marks `SHIPPING`.
