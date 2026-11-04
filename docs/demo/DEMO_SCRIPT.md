# I-ITSM — Demo Video Script (15–20 min, 3 members)

**File name for upload:** `<GroupNo>_OOP_S1_2025-2026_Project.mp4` (exactly as the brief specifies, e.g. `7_OOP_S1_2025-2026_Project.mp4`)

**Rules from the brief to satisfy:** every member introduces themself on camera · each member explains at least one module · at least three complete workflows demonstrated · working application shown (not slides only).

## Before recording (10 minutes)
1. `docker compose up --build` (or `mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `npm run dev`). Wait for "Seed complete" in the backend log.
2. **Speed up SLA for the demo**: login as `admin` → *Services, categories, SLA* → set **P1 response 1 min / resolution 3 min, warn at 60 %** → Save. (The SLA monitor runs every 30 s; `dev` profile every 10 s with escalation at 2 min.)
3. Open three browser windows (or one normal + two incognito) logged in as `emp.raj`, `tech.arjun`, `admin`. Put the admin **Live dashboard** on screen in the background.
4. Have `docs/diagrams/*.png` and the IDE open with `IncidentStates.java`, `StrategyRegistry.java`, `IncidentEventBus.java`.
5. Record at 1080p, mic check, close notifications.

---

## Segment 0 — Introduction (all, 1.5 min)
- Each member: name, ID, and one line on what they owned (Member 1 domain model & OOP; Member 2 backend/API/persistence; Member 3 frontend/testing/docs).
- One sentence on the problem: "hundreds of IT incidents a day; we built I-ITSM so they are reported, prioritised, assigned, SLA-tracked, resolved and audited automatically."
- 30-second architecture slide (`architecture.png`): React → Spring Boot → PostgreSQL; event bus with observers; Docker.

## Segment 1 — Member 1: Workflow 1 — *Employee reports → system prioritises → technician assigned → notifications* (5 min)
**Screen: `emp.raj` window**
1. Show login page, mention JWT + BCrypt in one sentence. Log in as employee.
2. *Report an incident*: service **Payment Application**, title "Payment application unavailable", description "Customers cannot complete checkout, payment page shows 500 error". **Pause** to show the live **category suggestion** (APPLICATION) and that no similar incidents exist yet. Impact CRITICAL, urgency HIGH, 400 users. Submit.
3. On the detail page point out: incident number, **P1**, the **priority rationale** string (impact 4 × urgency 3 × criticality 4 = 48 + 8 users bonus = 56 → P1), the **SLA meter** with deadlines, **assigned technician** (Arjun — smart recommendation), and the **audit timeline** with CREATED → CLASSIFIED → ASSIGNED.
4. Click the 🔔 bell: in-app notification "Incident created", "assigned to Arjun Mehta".
5. **Code moment (1 min):** open `Incident.java` — no `setStatus`; `acknowledge()` delegates to `IncidentStates` → State pattern. Show `NewState` / `ClosedState` and that defaults throw `InvalidStateTransitionException`. Show the enum weights in `Impact` and `WeightedScoringPriorityStrategy.calculate`.
6. **Duplicate demo:** back in the form, submit "Payment transaction failing / unable to make payment, checkout error 500" on the same service → the warning "possible duplicate of INC-000001" appears; click *submit anyway* → it is created and linked ("Related: INC-000001"). Mention Jaccard similarity.

## Segment 2 — Member 2: Workflow 2 — *Technician investigates → SLA monitored → resolved → user notified* (6 min)
**Screen: `tech.arjun` window**
1. *My queue*: capacity 1/6, the P1 at the top sorted by priority. Open it.
2. **Acknowledge** (SLA "responded" stamped) → **Start investigation** → **Request information** ("Which browser? all customers?") → status PENDING_INFO. Try nothing illegal yet.
3. Switch to `emp.raj`: bell shows "More information needed"; open incident → **Reply with information**. Switch back: status IN_PROGRESS, note thread visible.
4. **Illegal transition demo:** in a terminal (or Postman) `curl -X POST .../api/incidents/1/close` with the technician token → `409 INVALID_STATE_TRANSITION`; and with the employee token call `/acknowledge` → `403 UNAUTHORIZED_OPERATION`. One sentence: state pattern + ownership checks.
5. **SLA live:** with the shortened P1 policy, wait for the dashboard/bell to show **"SLA warning"** then **"SLA VIOLATED"** (admin and technician get email + SMS + in-app — show *Audit log & outbox → email outbox*). Alternatively click *Run SLA monitor now* on the admin dashboard.
6. **Resolve**: summary "Restarted payment gateway pods", root cause "expired TLS certificate". Show SLA status (MET or VIOLATED), suggested-resolutions panel (will be empty first time — explain it learns from history).
7. Switch to `emp.raj`: notification "resolved"; **Verify fix** → **Give feedback** 5★ → **Close**. Show the full **audit trail** end to end.
8. **Code moment (1.5 min):** `IncidentEventBus.publish` looping over observers; `AuditObserver`, `NotificationObserver` routing table, `NotificationChannel` interface with Email (adapter) / SMS / In-app; `SLAMonitor.tick()` with `@Scheduled`; `IncidentRepository` + `IncidentSpecifications` (Repository pattern). Show the Postgres tables in `docs/database/schema.sql` or psql `\dt`.

## Segment 3 — Member 3: Workflow 3 — *Multiple incidents → duplicates/related → correlation & escalation; admin configuration, reports, tests* (5.5 min)
**Screen: `admin` window**
1. **Live dashboard** on screen; from the employee window create a P1 on **Single Sign-On** ("SSO login completely down, MFA page times out", CRITICAL/CRITICAL, 3000 users). Watch the event stream update instantly (WebSocket) → CREATED, CLASSIFIED (AUTHENTICATION), ASSIGNED (Priya).
2. Don't acknowledge it. After the escalation period (2 min in dev / "Run SLA monitor now") show **ESCALATED** in the stream: reassigned to team lead Arjun, admins notified. Open the incident → "Escalated L1" badge.
3. **Correlation**: create a second SSO report from `emp.meera` ("Cannot sign in, OTP never arrives") → duplicate warning; submit anyway → related link; open the incident → Intelligence panel lists related open incidents with % similarity.
4. **Admin configuration** (`Services, categories, SLA`): switch assignment strategy to ROUND_ROBIN / priority to RULE_BASED live; show SLA policy table; add a new category "VOIP" with keywords "phone,dial,voip,call" → go to *Users & technicians* and give Rahul the VOIP skill → report "Desk phone cannot dial" → auto-classified VOIP and assigned to Rahul. **"New category without code change."**
5. **Search & reports**: filter by status / priority / SLA status / date; *Reports* page — MTTA, MTTR, SLA compliance %, by service/category, workload. *Audit log* tab.
6. **Tests**: terminal `mvn test` → `Tests run: 44, Failures: 0`. Show one test (`IncidentLifecycleTest.cannotCloseNewIncident`) and the test table in the report.
7. **Docker**: `docker compose up` already running — show `docker ps` with the three containers.

## Segment 4 — Wrap-up (all, 1 min)
- Rubric mapping in one breath: MVP ✔, 6 design patterns, 6 intelligent features, 44 tests, real-time dashboard + Docker (innovation).
- Limitations (simulated email/SMS, single instance) and what you'd do next (LLM classification, dependency graph).
- Thank the evaluators; repository link on screen.

---

## Timing budget
| Segment | Minutes |
|---|---|
| 0 Intro + architecture | 1.5 |
| 1 Workflow 1 + State/Strategy code | 5 |
| 2 Workflow 2 + Observer/SLA code | 6 |
| 3 Workflow 3 + config/reports/tests/Docker | 5.5 |
| 4 Wrap-up | 1 |
| **Total** | **19** |

## Recovery tips while recording
- If auto-assignment says "no technician available": the technician is at capacity → raise `maxWorkload` in *Users & technicians*.
- If a duplicate blocks a report you wanted created: tick "submit anyway".
- If the WebSocket badge says "connecting…": refresh the dashboard tab once.
- If you need the SLA to breach *now*: *Live dashboard → Run SLA monitor now*.
- Reset everything: `docker compose down -v && docker compose up` (fresh seed data).
