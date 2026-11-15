# I-ITSM — Intelligent IT Service Management & Incident Resolution System

CS F213 / MAC F212 Object-Oriented Programming · Semester I, 2026-27 · Group project

An end-to-end incident management platform covering the complete lifecycle
**Report → Classify → Prioritize → Assign → Investigate → Resolve → Verify → Close**,
built to demonstrate object-oriented design on a real organisational problem.

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 3.3 (Web, Data JPA, Security, WebSocket, Validation) · JWT |
| Frontend | React 18 · Vite · React Router · STOMP over WebSocket · Inter + Instrument Serif (bundled) |
| Database | PostgreSQL 16 (H2 in-memory for the `dev` profile and tests) |
| AI (optional) | Claude Sonnet 5.5 via the official Anthropic Java SDK · rules mode when no key is set |
| Deployment | Docker Compose (Postgres + backend + nginx-served frontend) |
| Tests | JUnit 5 · Spring MockMvc · 111 tests / 88 documented test cases |

## Quick start

### Option A — Docker (one command)
```bash
docker compose up --build
```
Frontend: <http://localhost:5173> · API: <http://localhost:8080> · Postgres: `localhost:5432` (`itsm`/`itsm`)

With no key set, the app runs in rules mode: everything works, and the AI features answer from fixed rules.
To turn on the optional AI features, keep your key in a file outside the project and start with
`docker compose --env-file ~/.config/i-itsm/ai.env up --build -d` instead; see [AI features (optional)](#ai-features-optional).
`docker compose down` stops the app. `docker compose down -v` also deletes the database, so the next start seeds a fresh one (do this before recording a demo).

### Option B — Local, no database install needed (H2 in memory)
```bash
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
```
Open <http://localhost:5173>. The `dev` profile also exposes the H2 console at <http://localhost:8080/h2-console>
(JDBC URL `jdbc:h2:mem:itsm`, user `sa`).
The backend needs JDK 17 and Maven (`mvn -v` shows which Java Maven uses); the frontend needs Node 18 or newer.

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
Run them on JDK 17. Maven should report `Tests run: 111, Failures: 0, Errors: 0, Skipped: 0`.
If `mvn -v` shows another Java version, point `JAVA_HOME` at a JDK 17 for the command (Homebrew on an Apple-silicon Mac: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn test`).
The AI tests use a fake gateway (or, for the retry tests, a fake Anthropic API on 127.0.0.1 with a made-up key) instead of Claude.
The test profile starts with the AI switched off, so `mvn test` needs no API key, never calls Anthropic, even if a key is set on the machine, and costs nothing.

## AI features (optional)

Four features use Claude (Anthropic's model Claude Sonnet 5.5, called through the official Anthropic Java SDK).
They only make suggestions. A person confirms every draft and every filter, and the app works fully without them.

| Feature | What it does |
|---|---|
| Screenshot to ticket | On *Report a problem*, paste, drop or choose a screenshot of the error. Claude reads it and fills in a draft: title, description, service, category, impact, urgency and affected users. Each field it filled is tagged **AI**. You check it, change anything you like, and send it as usual. |
| Chat reporting | On *Report a problem*, choose *Describe it in a chat instead* and describe the problem in a short chat. Claude asks up to three short questions, then proposes a draft for you to confirm. The chat never creates the incident itself. |
| Plain-English search | In the search at the top of the page (or ⌘K / Ctrl+K), type a sentence such as *"urgent VPN problems assigned to Priya last week"*. It becomes the normal search filters, shown as filter words you can remove. Claude never touches the database. |
| Plain-language updates | When an incident is assigned, acknowledged, waiting for information or resolved, the reporter's in-app notification (the bell) gets a friendly 1–2 sentence version a few seconds later. It is written in the background after the change is saved, so nothing waits for it; the email copy keeps the standard text. The admin Reports page also gets an "In plain words" summary. |

### Turn them on
1. Get an API key from Anthropic (the account needs some credit).
2. Keep the key **outside the project folder and outside any synced folder** (Google Drive, iCloud Desktop & Documents, OneDrive, Dropbox).
   A synced folder uploads every file in it, and a key file inside the project is one `git add` away from a commit.
   A good place is `~/.config/i-itsm/ai.env`:
   ```bash
   mkdir -p ~/.config/i-itsm
   nano ~/.config/i-itsm/ai.env        # one line: ANTHROPIC_API_KEY=sk-ant-...
   chmod 600 ~/.config/i-itsm/ai.env
   ```
   Type the key in the editor, not with `echo`, so it stays out of your shell history.
3. Start the app with that file:
   ```bash
   docker compose --env-file ~/.config/i-itsm/ai.env up --build -d
   docker compose logs -f backend      # optional: watch it start ("Seed complete")
   ```
   (PowerShell: write the path as `$HOME\.config\i-itsm\ai.env`.)
   For options B and C, load the file into the shell that starts the backend:
   ```bash
   set -a; . ~/.config/i-itsm/ai.env; set +a     # then start the backend from this shell
   ```
   (PowerShell: `Get-Content $HOME\.config\i-itsm\ai.env | ForEach-Object { $n, $v = $_ -split '=', 2; Set-Item "env:$n" $v }`.)

Without a key the app runs in rules mode.
With Docker Compose, a key that is already exported as `ANTHROPIC_API_KEY` in your shell wins over the file, and it switches the AI on even when you pass no file, so calls are billed to that key; run `unset ANTHROPIC_API_KEY` first if that is not what you want.
If an older setup left the key in a `.env` file inside the project, move it to the file above and delete that line.
Docker Compose reads a `.env` file in the project folder by itself whenever you start without `--env-file`, so a key left there still switches the AI on.
If the folder was ever synced, revoke that key in the Anthropic Console.
The key is read only from the `ANTHROPIC_API_KEY` environment variable. It is never logged and never sent to the browser.

### Rules mode and the indicator
Without a key, or whenever Claude can't be used, the app runs in **rules mode**, and nothing is sent to Anthropic.
Each feature then answers from fixed rules:
- screenshot to ticket: the image is not read, and you describe the problem in the form (the keyword category suggestion and the duplicate check still run);
- chat reporting: one reply points you back to the form, with no questions and no draft; when you close the chat, what you typed is copied into the form;
- plain-English search: the sentence is searched as typed, in the incident number, title and description, so one word such as `vpn` (or a number such as `INC-12`) works best;
- plain-language updates and the summary: fixed templates filled in with the real details and figures.

Every page after sign-in shows which mode you are in: a moving wave and "AI live", or a flat line and "Rules mode" with the reason.
(The sign-in page has no indicator, because the status needs a signed-in user.)

| Reason | Meaning |
|---|---|
| `NO_KEY` | `ANTHROPIC_API_KEY` is not set |
| `DISABLED` | an admin switched the AI off |
| `DAILY_LIMIT` | the day's call limit (150 by default) is used up; it resets at midnight India time |
| `CREDITS_EXHAUSTED` | the Anthropic account has run out of credit, or reached a spend limit set on it; rules until midnight India time, or until an admin switches the AI on again |
| `KEY_INVALID` | Anthropic rejected the key; put a working key in the file and start the app again |
| `UNAVAILABLE` | two calls in a row got no answer from Claude (rate limit, server error, time-out, network error, or a 404 such as a wrong model id); every feature uses rules for a 60-second cool-down, then tries Claude again |

A single failed call only makes that one request fall back.
After a 408, 429 or 5xx answer or a dropped connection, the backend tries Claude once more half a second later, but only while at least half of the feature's time budget (8 seconds, 6 for updates) is left and only within what remains, so no call takes longer than its budget and a call that timed out is never retried.

`GET /api/ai/status` returns the mode, the reason, the model id, today's call count and the daily limit (never the key).

### Admin switch
An admin can switch the AI off and on while the app runs (*Settings* → *AI assistance*, `PUT /api/admin/ai`), with no restart.
This is handy for showing rules mode in a demo.
Switching it on also clears a rejected key, used-up credit and a running cool-down, so the next call checks again.

### Cost and privacy
A typical AI call costs about 1 US cent (Claude Sonnet 5.5 at low effort).
With the default limit of 150 calls a day, a full day costs about US$1.50.

When the AI is live, these are sent to Anthropic's API: report and chat text, search sentences, pasted screenshots, the service and category catalogue (screenshot, chat and search), the names of active technicians (search), the incident details needed for an update, including the technician's name, and the report figures with technician names and workload (summary).
Don't paste screenshots that show passwords or personal data.

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
| ≥ 2 intelligent features | Duplicate detection (Jaccard similarity), auto-categorisation (keyword rules), smart technician recommendation (weighted score), SLA risk detection, automatic escalation, suggested resolutions; plus four optional AI features (next row) |
| Innovation: AI assistance (optional) | `ai/*` — `AiGateway`, a generic port (`hasCredentials()`, `complete(AiRequest)` → JSON), and `ClaudeAiGateway`, the Adapter over the Anthropic Java SDK and the only class that calls Claude (a fake replaces it in tests); `AiModeService` (AI live or rules mode, daily limit, cool-down); feature services that each fall back to their own rules method; `PlainLanguageUpdateService` (after-commit rewrite of the reporter's in-app line); `POST /api/ai/screenshot-triage\|chat\|search`, `GET /api/ai/status`, `PUT /api/admin/ai`, `GET /api/admin/reports/summary` |
| Guarded lifecycle (e.g. NEW → CLOSED impossible) | State pattern: `incident/IncidentState`, `IncidentStates` |
| Custom exceptions | `exception/*` (12 classes) + `web/GlobalExceptionHandler` |
| Persistence | PostgreSQL via Spring Data JPA; DDL in `docs/database/schema.sql` |

### Design patterns
| Pattern | Role in the system |
|---|---|
| **State** | `IncidentState` + 9 concrete states own the legal transitions of an `Incident` |
| **Strategy** | `PriorityStrategy` (2) and `AssignmentStrategy` (4); the admin swaps them at runtime |
| **Observer** | `IncidentEventBus` publishes `IncidentEvent`s to `AuditObserver`, `NotificationObserver` and `DashboardBroadcastObserver`. For the AI plain-language updates, `NotificationObserver` hands the reporter's saved notifications to `PlainLanguageUpdateService`, which rewrites them after commit on a background thread |
| **Factory** | `NotificationFactory` (event → message), `UserFactory` (role → `User` subclass) |
| **Repository** | Spring Data repositories + `IncidentSpecifications` |
| **Adapter** | `EmailNotificationChannel` adapts an `EmailGateway` (simulated SMTP, swappable for a real provider); `ClaudeAiGateway` adapts the Anthropic Java SDK to the `AiGateway` port |

## Project layout
```
backend/   Spring Boot application (src/main/java/edu/bits/itsm/…)
  domain/        entities, value objects, State & Strategy pattern classes
  event/         Observer pattern: event bus + observers
  notification/  channels, factory, dispatcher, adapter
  intelligence/  duplicate detection, categorisation, resolution suggestion
  ai/            optional AI features: AiModeService, gateway/ (AiGateway port, ClaudeAiGateway adapter), feature/ (services with rules fallbacks)
  service/       application services, SLA monitor / escalation engine
  web/           REST controllers, DTOs, global exception handler
  security/      JWT issuing/validation, security configuration
frontend/  React single-page app (src/pages/*, src/components/*, src/lib/*)
docs/      report, UML diagrams, database schema, demo scripts
```

## Documentation
- Project report: [docs/report/I-ITSM_Project_Report.pdf](docs/report/I-ITSM_Project_Report.pdf), made from [docs/report/report.html](docs/report/report.html)
- Demo video scripts: [docs/demo/DEMO_SCRIPT_v3.md](docs/demo/DEMO_SCRIPT_v3.md) (a story, then how it works) and [docs/demo/DEMO_SCRIPT.md](docs/demo/DEMO_SCRIPT.md) (v2)
- UML and architecture diagrams: [docs/diagrams/](docs/diagrams/) (Mermaid `.mmd` and Graphviz `.dot` sources next to the rendered `.png` files)
- Database: [docs/database/schema.sql](docs/database/schema.sql) and [docs/database/sample_data.sql](docs/database/sample_data.sql)

## API overview
`POST /api/auth/register|login` · `GET /api/auth/me`
`POST /api/incidents` (any signed-in user; the UI offers it to employees and admins) · `GET /api/incidents?q&status&priority&categoryId&serviceId&technicianId&slaStatus&from&to`
`GET /api/incidents/{id}` · `/history` · `/similar` · `/recommendations` · `/suggested-resolutions`
`POST /api/incidents/{id}/{classify|assign|auto-assign|acknowledge|start|notes|request-info|provide-info|resolve|verify|reopen|close|feedback}`
`GET /api/services|categories|technicians|sla-policies|strategies|notifications`
`/api/admin/**` — users, technicians, services, categories, SLA policies, strategies, reports, audit log, email outbox, `POST /api/admin/sla/run-monitor`
AI (optional): `GET /api/ai/status` and `POST /api/ai/search` (any signed-in user) · `POST /api/ai/screenshot-triage|chat` (employees and admins; a technician gets 403) · suggestions only, nothing is created · admin: `PUT /api/admin/ai` · `GET /api/admin/reports/summary`
WebSocket: `ws://host/ws` (STOMP) — topics `/topic/incidents`, `/topic/user/{userId}`
