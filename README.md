# azure-cloud-native-order-platform

A step-by-step cloud-native order platform, built on **Microsoft Azure**.
This is the Azure port of `aws-cloud-native-order-platform` — same features,
same REST API, but Azure services instead of AWS.

Architecture:

```
Spring Boot
    ↓
REST API  (orders + customers)
    ↓
Azure SQL Database  (orders + customer tables)
    ↓
Service Bus Topic → 5 Subscriptions (processor / lambda / email / sms / shipping)
```

| AWS (original)                | Azure (this project)                          |
|-------------------------------|-----------------------------------------------|
| DynamoDB (Enhanced Client)   | Azure SQL Database (Spring Data JPA)          |
| SNS topic + 5 SQS queues      | Service Bus **Topic + 5 Subscriptions**       |
| SES (email)                   | Azure Communication Services **Email**        |
| SNS SMS                       | Azure Communication Services **SMS**          |
| ElastiCache Redis             | Azure Cache for Redis (spring-data-redis)     |
| CloudFormation security stack | **Bicep** (Key Vault, Managed Identity, RBAC, AKS) |
| DynamoDB Local + LocalStack   | Azure SQL Edge + Service Bus emulator (docker-compose) |

## Asynchronous processing (Step 3)

After an order is created, the API publishes an event that fans out through
Azure Service Bus:

```
                    ┌── Subscription ──► Order Processor   (marks order "PROCESSED")
                    │
                    ├── Subscription ──► Function-like     (marks order "NOTIFIED")
                    │
Order API ─► Topic ─┼── Subscription ──► Email handler    (ACS confirmation email)
    (create)        │
                    ├── Subscription ──► SMS handler       (ACS text message)
                    │
                    └── Subscription ──► Shipping processor (marks order "SHIPPING")
```

- **Topic** (`order-events`) fans out to **five** subscriptions.
- **Order Processor** subscription → the in-app `OrderProcessor`, an
  `@Scheduled` poller that marks the order `PROCESSED`.
- **Function** — for local development this is emulated as a subscription
  (`order-lambda`) consumed by `OrderLambdaHandler` (marks the order
  `NOTIFIED`). In Azure, replace this with a real **Azure Function** triggered
  by the topic; the subscription + poller then become unnecessary.
- **Email** — `EmailNotificationHandler` consumes the `order-email` subscription,
  looks up the customer, and sends a confirmation email through **ACS Email**.
- **SMS** — `SmsNotificationHandler` consumes the `order-sms` subscription,
  looks up the customer, and sends a text message via **ACS SMS**.
- **Shipping** — `ShippingProcessor` consumes the `order-shipping` subscription
  and marks the order `SHIPPING`.

Publishing is **best-effort** and non-blocking: it never fails the order
creation response, and the app still boots even if messaging resources are
unavailable.

### Local emulation

`docker-compose.yml` starts **Azure SQL Edge** (SQL Server compatible) and the
**Azure Service Bus emulator** alongside Redis. The app points at the emulator
via `azure.servicebus.connection-string` (`UseDevelopmentEmulator=true`), and
at SQL Edge via the `azure` profile (`application-azure.yml`). Resources
(topic, subscriptions) are provisioned idempotently on startup.

> The emulator records Service Bus traffic; ACS Email/SMS no-op locally (they
> simply log) because real delivery requires a provisioned Azure Communication
> Services resource.


## Security (Step 4)

Security is defined as infrastructure-as-code (Bicep) — Managed Identity, Key
Vault, role-based access control (RBAC), and AKS. See
[`docs/GUIDE.md`](docs/GUIDE.md) — "Step 4".

## Redis caching (Step 5)

`GET /orders/{id}` is cached in **Redis** (Azure Cache for Redis) using the
cache-aside pattern. See [`docs/GUIDE.md`](docs/GUIDE.md) — "Step 5".

## Documentation

- **Complete guide** (what it is + how it was built, line by line): [`docs/GUIDE.md`](docs/GUIDE.md)

## Stack

- Java 21
- Spring Boot 3.3.x
- Spring Data JPA (Azure SQL Database)
- Azure SDK for Java (Service Bus, Communication Services Email/SMS, Identity)
- Azure SQL Edge (via Docker) for development
- Azure Service Bus emulator for local messaging
- Redis (cache-aside for reads; Azure Cache for Redis in production)

## Prerequisites

- JDK 21
- Gradle (or use the included wrapper)
- Docker (for local SQL Edge, Service Bus emulator, and Redis)

## Run locally

The app boots with **zero external dependencies** by default (in-memory H2 +
no-op messaging), so you can start it right away:

```bash
./gradlew bootRun                    # Linux / macOS
gradlew.bat bootRun                  # Windows
```

For a full Azure-like local environment, start the containers and use the
`azure` profile:

```bash
docker compose up -d
gradlew.bat bootRun --args='--spring.profiles.active=azure'
```

### Swagger UI

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### REST API

| Method | Endpoint              | Description            |
|--------|-----------------------|------------------------|
| POST   | `/api/customers`      | Create a customer      |
| GET    | `/api/customers/{id}` | Get a customer by id   |
| POST   | `/api/orders`         | Create an order        |
| GET    | `/api/orders/{id}`    | Get an order by id     |
| GET    | `/api/orders`         | List all orders        |
| PUT    | `/api/orders/{id}`    | Update an order        |
| DELETE | `/api/orders/{id}`    | Delete an order        |

> An order must reference an existing customer: creating an order with a missing
> or unknown `customerId` returns `404`. The order's `customerId` is immutable
> after creation — attempts to change it return `400`.

### Example: create an order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-123",
    "items": [
      { "productId": "p-1", "productName": "Laptop", "quantity": 1, "unitPrice": 1200.00 },
      { "productId": "p-2", "productName": "Mouse", "quantity": 2, "unitPrice": 25.50 }
    ]
  }'
```

## Testing

```bash
./gradlew test                      # Linux / macOS
gradlew.bat test                    # Windows
```

Unit tests (service, controller, publisher, consumers) run with Mockito. The
integration tests run against an **in-memory H2** database (no Docker required)
and mock the Azure Service Bus / Communication Services clients, so the full
build stays green on any machine.

## Configuration

Configuration is in `src/main/resources/application.yml` (and
`application-azure.yml` for the Azure SQL profile). Key properties:

- `azure.servicebus.connection-string` — Service Bus namespace connection string
  (or the emulator). Env override: `SERVICEBUS_CONNECTION_STRING`.
- `azure.servicebus.topic-name` — topic name (default `order-events`)
- `azure.servicebus.*-subscription` — the five fan-out subscription names
- `azure.communication.connection-string` — ACS connection string (leave blank
  locally; email/SMS then no-op). Env override: `ACS_CONNECTION_STRING`.
- `azure.communication.sender-email` / `from-phone-number` — ACS sender details
- `azure.currency` — currency used in order confirmation emails (default `AED`)
- `spring.datasource.*` — datasource (H2 by default; Azure SQL via the `azure` profile)

When running in Azure, use Managed Identity (empty connection strings) and rely
on `DefaultAzureCredential`; secrets are injected via environment variables or
Key Vault.
