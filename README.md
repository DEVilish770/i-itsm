# I-ITSM — Intelligent IT Service Management & Incident Resolution System

CS F213 / MAC F212 Object-Oriented Programming · Semester I, 2026-27 · Group project

An end-to-end incident management platform covering the complete lifecycle
**Report → Classify → Prioritize → Assign → Investigate → Resolve → Verify → Close**,
built to demonstrate object-oriented design on a real organisational problem.

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 3.3 (Web, Data JPA, Security, WebSocket, Validation) · JWT |
| Frontend | React 18 · Vite · React Router · STOMP over WebSocket |
| Database | PostgreSQL 16 (H2 in-memory for the `dev` profile and tests) |
| Deployment | Docker Compose (Postgres + backend + nginx-served frontend) |
| Tests | JUnit 5 · Spring MockMvc · 44 tests / 33 documented test cases |

## Quick start

### Option A — Docker (one command)
```bash
docker compose up --build
```
Frontend: <http://localhost:5173> · API: <http://localhost:8080> · Postgres: `localhost:5432` (`itsm`/`itsm`)

### Option B — Local, no database install needed (H2 in memory)
```bash
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
```
Open <http://localhost:5173>. The `dev` profile also exposes the H2 console at `/h2-console`
(JDBC URL `jdbc:h2:mem:itsm`, user `sa`).

### Option C — Local with PostgreSQL
```bash
createuser itsm -P            # password: itsm
createdb -O itsm itsm
cd backend && mvn spring-boot:run        # reads DB_URL / DB_USER / DB_PASSWORD env vars if set
```

### Demo accounts (password for all: `password123`)
| Username | Role | Notes |
|---|---|---|
| `admin` | Administrator | full configuration, live dashboard, reports |
| `tech.arjun` | Technician, team lead | APPLICATION, DATABASE, CLOUD · 8 yrs |
| `tech.priya` | Technician | NETWORK, SECURITY, AUTHENTICATION · 5 yrs |
| `tech.rahul` | Technician | HARDWARE, SOFTWARE, EMAIL, OTHER · 2 yrs |
| `tech.sneha` | Technician | STORAGE, CLOUD, DATABASE, APPLICATION · 4 yrs |
| `emp.raj`, `emp.meera` | Employees | report incidents; new employees can self-register |

### Run the tests
```bash
cd backend && mvn test
```

## What is implemented (mapped to the MVP checklist)

| Requirement | Where |
|---|---|
| 3 user roles with role-based authorization | `domain/user/*`, `security/SecurityConfig`, `@PreAuthorize` on `AdminService`, ownership checks in `IncidentService` |
| Incident create / view / update / assign / resolve / close | `IncidentService`, `IncidentController` |
| Priority from impact × urgency × business criticality (+ affected users) | `strategy/priority/WeightedScoringPriorityStrategy`, `RuleBasedPriorityStrategy` |
| ≥ 2 assignment strategies, switchable at runtime | `strategy/assignment/*` (4 strategies) + `StrategyRegistry` + `PUT /api/admin/strategies` |
| SLA calculation, deadline tracking, warnings, violation detection, background monitoring | `sla/SLAPolicy`, `sla/SLATracker`, `service/SLAMonitor` (`@Scheduled`) |
| ≥ 2 notification mechanisms | `notification/EmailNotificationChannel` (simulated SMTP via Adapter), `SmsNotificationChannel`, `InAppNotificationChannel` (WebSocket push) |
| Complete audit history | `event/observers/AuditObserver` → `audit_records` table; `GET /api/incidents/{id}/history` |
| ≥ 5 search / filter criteria | `repository/IncidentSpecifications` — text, status, priority, category, service, technician, SLA status, date range (8) |
| Reports / dashboards | `ReportService`; live dashboard pushed over STOMP (`DashboardBroadcastObserver`) |
| ≥ 2 intelligent features | Duplicate detection (Jaccard similarity), auto-categorisation (keyword rules), smart technician recommendation (weighted score), SLA risk detection, automatic escalation, suggested resolutions |
| Guarded lifecycle (e.g. NEW → CLOSED impossible) | State pattern: `incident/IncidentState`, `IncidentStates` |
| Custom exceptions | `exception/*` (11 classes) + `web/GlobalExceptionHandler` |
| Persistence | PostgreSQL via Spring Data JPA; DDL in `docs/database/schema.sql` |

### Design patterns
| Pattern | Role in the system |
|---|---|
| **State** | `IncidentState` + 9 concrete states own the legal transitions of an `Incident` |
| **Strategy** | `PriorityStrategy` (2) and `AssignmentStrategy` (4); the admin swaps them at runtime |
| **Observer** | `IncidentEventBus` publishes `IncidentEvent`s to `AuditObserver`, `NotificationObserver`, `DashboardBroadcastObserver` |
| **Factory** | `NotificationFactory` (event → message), `UserFactory` (role → `User` subclass) |
| **Repository** | Spring Data repositories + `IncidentSpecifications` |
| **Adapter** | `EmailNotificationChannel` adapts an `EmailGateway` (simulated SMTP, swappable for a real provider) |

## Project layout
```
backend/   Spring Boot application (src/main/java/edu/bits/itsm/…)
  domain/        entities, value objects, State & Strategy pattern classes
  event/         Observer pattern: event bus + observers
  notification/  channels, factory, dispatcher, adapter
  intelligence/  duplicate detection, categorisation, resolution suggestion
  service/       application services, SLA monitor / escalation engine
  web/           REST controllers, DTOs, global exception handler
  security/      JWT issuing/validation, security configuration
frontend/  React single-page app (src/pages/*, src/components/*, src/lib/*)
docs/      report, UML diagrams, database schema, demo script
```

## API overview
`POST /api/auth/register|login` · `GET /api/auth/me`
`POST /api/incidents` · `GET /api/incidents?q&status&priority&categoryId&serviceId&technicianId&slaStatus&from&to`
`GET /api/incidents/{id}` · `/history` · `/similar` · `/recommendations` · `/suggested-resolutions`
`POST /api/incidents/{id}/{classify|assign|auto-assign|acknowledge|start|notes|request-info|provide-info|resolve|verify|reopen|close|feedback}`
`GET /api/services|categories|technicians|sla-policies|strategies|notifications`
`/api/admin/**` — users, technicians, services, categories, SLA policies, strategies, reports, audit log, email outbox, `POST /api/admin/sla/run-monitor`
WebSocket: `ws://host/ws` (STOMP) — topics `/topic/incidents`, `/topic/user/{userId}`
