# Eventide

A configurable event choreography platform that lets you define event-driven workflows without writing code. Define rules like *"When `customer.created` arrives from `user-service`, if `plan == enterprise`, publish to `onboarding-enterprise` topic and call webhook"* — and Eventide handles the rest.

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Producers   │────▶│    Kafka     │────▶│  Choreography    │
│ (any service)│     │   Topic      │     │  Engine          │
└──────────────┘     └──────────────┘     └────────┬─────────┘
                            ▲                      │
                            │              ┌───────┼───────────────────┐
                            │              │       │                   │
                    ┌───────┴───────┐  ┌───▼───────▼────────┐  ┌──────▼─────────┐
                    │  DLQ Retry    │  │      Redis         │  │    Action      │
                    │  Consumer     │  │  (deduplication,   │  │    Dispatcher  │
                    │  (backoff +   │  │   event tracking)  │  │  (Kafka, HTTP, │
                    │   re-publish) │  │                    │  │   Webhooks)    │
                    └───────▲───────┘  └────────────────────┘  └──────┬─────────┘
                            │                                         │ (failure)
                    ┌───────┴───────┐                          ┌──────▼─────────┐
                    │ eventide.dlq  │◀─────────────────────────│  Dead Letter   │
                    │ (retry queue) │                          │  Queue Service │
                    └───────┬───────┘                          └────────────────┘
                            │ (max retries exceeded)
                    ┌───────▼──────────┐     ┌───────────────┐
                    │ eventide.dlq.dead│     │  PostgreSQL   │
                    │ (permanent)      │     │  (workflow    │
                    └──────────────────┘     │   definitions)│
                                            └───────────────┘
```

### How It Works

1. **Events arrive** via Kafka topic `eventide.events` (or HTTP POST to `/api/events`)
2. **Deduplication** — Redis checks if this event was already processed (prevents duplicates)
3. **Workflow matching** — Engine looks up active workflows by `eventType` + `source`
4. **Rule evaluation** — Each rule's condition is evaluated against the event payload
5. **Action dispatch** — Matching rules trigger actions: publish to Kafka, call webhooks, or make HTTP requests
6. **Dead-letter queue** — Failed actions are sent to `eventide.dlq` for automatic retry with exponential backoff
7. **Auto-retry** — DLQ consumer retries failed events up to 3 times (5s → 25s → 125s backoff), then parks in `eventide.dlq.dead`

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
| `payload.field >= 100` | Numeric greater than or equal |
| `payload.field < 100` | Numeric less than |
| `payload.field <= 100` | Numeric less than or equal |
| `payload.nested.field == 'value'` | Nested field access |
| *(empty or null)* | Always matches (catch-all) |

## Tech Stack

| Component | Technology |
|---|---|
| Backend | Java 17+, Spring Boot 3.4 |
| Event Streaming | Apache Kafka (KRaft mode) |
| Database | PostgreSQL 12+ |
| Cache / Dedup | Redis 7 |

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL, Apache Kafka, Redis (via Homebrew, Docker, or any package manager)

### Setup

```bash
# Install dependencies (macOS example)
brew install postgresql kafka redis

# Start services
brew services start postgresql
brew services start kafka
brew services start redis

# Create the database
createdb eventide

# Build and run the application
mvn spring-boot:run

# Verify
curl http://localhost:8080/api/workflows
```

> **Docker alternative:** If you have Docker installed, run `docker compose up -d` to start all infrastructure services at once.

### Run Tests

```bash
mvn test
```

## Project Structure

```
src/main/java/com/eventide/
├── config/
│   ├── AppConfig.java             # Bean definitions (RestTemplate)
│   └── EventideProperties.java    # Centralized topic & DLQ config
├── controller/         # REST API endpoints
├── dto/                # Request/Response objects
├── model/              # JPA entities (Workflow, WorkflowRule)
├── repository/         # Database access layer
└── service/
    ├── ChoreographyEngine.java    # Core engine (match + evaluate + dispatch)
    ├── ConditionEvaluator.java    # Expression parser for rule conditions
    ├── ActionDispatcher.java      # Executes actions (Kafka/Webhook/HTTP)
    ├── DeduplicationService.java  # Redis-based duplicate detection
    ├── DeadLetterQueueService.java # Failed event routing to DLQ
    ├── DlqRetryConsumer.java      # Auto-retries DLQ events with exponential backoff
    ├── EventListener.java         # Kafka consumer entry point
    └── WorkflowService.java       # CRUD operations for workflows
```

## Design Decisions

- **Choreography over Orchestration** — Services react to events independently rather than being controlled by a central coordinator. This reduces coupling and single points of failure.
- **Condition evaluation at runtime** — Rules are evaluated dynamically against event payloads, allowing workflow changes without redeployment.
- **Redis SET NX for dedup** — Atomic check-and-set with TTL provides thread-safe, distributed deduplication with automatic cleanup.
- **Dead-letter queue with auto-retry** — Failed actions are preserved in `eventide.dlq` and automatically retried with exponential backoff (5s → 25s → 125s). After 3 failures, events are parked in `eventide.dlq.dead` for manual investigation — ensuring no event is silently lost while preventing infinite retry loops.
- **Dedup clearance on retry** — Redis dedup keys are cleared before re-publishing a retried event, so the retry isn't blocked by the same dedup check that passed on the original attempt.
- **Externalized configuration** — All topic names, retry limits, and backoff delays are driven by `application.yml` via `@ConfigurationProperties` — no hardcoded strings, change once and it applies everywhere.

## Future Ideas

- **Compound conditions** — Support `AND` / `OR` operators in rule conditions (e.g., `payload.plan == 'enterprise' AND payload.region == 'US'`)
- **DLQ admin API** — REST endpoints to inspect, replay, and purge permanently failed events from `eventide.dlq.dead` (persist to DB for queryability)
- **Metrics & monitoring** — Spring Actuator + Prometheus metrics for event throughput, DLQ depth, retry rates, and action latencies
- **Retry with backoff tiers** — Separate Kafka topics per delay tier (`eventide.retry.30s`, `eventide.retry.5m`) instead of `Thread.sleep`, enabling non-blocking retries
- **Rate limiting** — Throttle action dispatch per workflow to prevent overwhelming downstream services
- **Multi-tenancy** — Namespace isolation so multiple teams can define workflows without conflicts
- **Event replay & audit log** — Persist all processed events to a log table for debugging, compliance, and replay capability
- **Workflow versioning** — Track rule changes over time and support rollback to previous versions

