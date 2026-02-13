# Eventide

A configurable event choreography platform that lets you define event-driven workflows without writing code. Define rules like *"When `customer.created` arrives from `user-service`, if `plan == enterprise`, publish to `onboarding-enterprise` topic and call webhook"* — and Eventide handles the rest.

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Producers   │────▶│    Kafka     │────▶│  Choreography    │
│ (any service)│     │   Topic      │     │  Engine          │
└──────────────┘     └──────────────┘     └────────┬─────────┘
                                                   │
                           ┌───────────────────────┼──────────────────┐
                           │                       │                  │
                   ┌───────▼───────┐    ┌──────────▼────────┐  ┌─────▼──────────┐
                   │  PostgreSQL   │    │      Redis        │  │    Action      │
                   │  (workflow    │    │  (deduplication,  │  │    Dispatcher  │
                   │   definitions │    │   event tracking) │  │  (Kafka, HTTP, │
                   │   & rules)   │    │                   │  │   Webhooks)    │
                   └───────────────┘    └───────────────────┘  └────────────────┘
```

### How It Works

1. **Events arrive** via Kafka topic `eventide.events` (or HTTP POST to `/api/events`)
2. **Deduplication** — Redis checks if this event was already processed (prevents duplicates)
3. **Workflow matching** — Engine looks up active workflows by `eventType` + `source`
4. **Rule evaluation** — Each rule's condition is evaluated against the event payload
5. **Action dispatch** — Matching rules trigger actions: publish to Kafka, call webhooks, or make HTTP requests
6. **Dead-letter queue** — Failed actions are sent to `eventide.dlq` for retry

## API

### Workflows

```bash
# Create a workflow
POST /api/workflows
{
  "name": "Customer Onboarding",
  "eventType": "customer.created",
  "source": "user-service",
  "rules": [
    {
      "priority": 1,
      "condition": "payload.plan == 'enterprise'",
      "actionType": "KAFKA",
      "actionConfig": "{\"topic\": \"onboarding-enterprise\"}"
    },
    {
      "priority": 2,
      "condition": "payload.plan == 'standard'",
      "actionType": "WEBHOOK",
      "actionConfig": "{\"url\": \"https://hooks.example.com/welcome\"}"
    }
  ]
}

# List all workflows
GET /api/workflows

# Get a specific workflow
GET /api/workflows/{id}

# Update a workflow
PUT /api/workflows/{id}

# Delete a workflow
DELETE /api/workflows/{id}

# Toggle workflow active/inactive
PATCH /api/workflows/{id}/toggle
```

### Events

```bash
# Submit an event (alternative to Kafka)
POST /api/events
{
  "eventId": "evt-abc-123",
  "eventType": "customer.created",
  "source": "user-service",
  "payload": {
    "name": "Lokesh",
    "plan": "enterprise",
    "email": "lokesh@example.com"
  }
}
```

### Condition Syntax

| Expression | Description |
|---|---|
| `payload.field == 'value'` | Equality check |
| `payload.field != 'value'` | Inequality check |
| `payload.field > 100` | Numeric greater than |
| `payload.field < 100` | Numeric less than |
| `payload.nested.field == 'value'` | Nested field access |
| *(empty or null)* | Always matches (catch-all) |

## Tech Stack

| Component | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.4 |
| Event Streaming | Apache Kafka (KRaft mode) |
| Database | PostgreSQL 16 |
| Cache / Dedup | Redis 7 |
| Containerization | Docker Compose |

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose

### Run

```bash
# Start infrastructure (Postgres, Kafka, Redis)
docker compose up -d

# Build and run the application
mvn spring-boot:run

# Verify
curl http://localhost:8080/api/workflows
```

### Run Tests

```bash
mvn test
```

## Project Structure

```
src/main/java/com/eventide/
├── config/             # App configuration (beans)
├── controller/         # REST API endpoints
├── dto/                # Request/Response objects
├── model/              # JPA entities (Workflow, WorkflowRule)
├── repository/         # Database access layer
└── service/
    ├── ChoreographyEngine.java    # Core engine (match + evaluate + dispatch)
    ├── ConditionEvaluator.java    # Expression parser for rule conditions
    ├── ActionDispatcher.java      # Executes actions (Kafka/Webhook/HTTP)
    ├── DeduplicationService.java  # Redis-based duplicate detection
    ├── DeadLetterQueueService.java # Failed event handling
    ├── EventListener.java         # Kafka consumer entry point
    └── WorkflowService.java       # CRUD operations for workflows
```

## Design Decisions

- **Choreography over Orchestration** — Services react to events independently rather than being controlled by a central coordinator. This reduces coupling and single points of failure.
- **Condition evaluation at runtime** — Rules are evaluated dynamically against event payloads, allowing workflow changes without redeployment.
- **Redis SET NX for dedup** — Atomic check-and-set with TTL provides thread-safe, distributed deduplication with automatic cleanup.
- **Dead-letter queue** — Failed actions are preserved in a separate Kafka topic rather than being silently dropped, enabling investigation and retry.

## License

MIT
