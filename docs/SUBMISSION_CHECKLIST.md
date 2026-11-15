# Submission checklist — deadline **22 Nov 2026** (hard, no extensions)

## A. Things only you can do (do these first)
- [x] **GitHub repo created:** <https://github.com/DEVilish770/i-itsm> (public; it is the `origin` remote).
- [ ] **Commit and push the AI features and the redesign.** They are still uncommitted on the local branch `ai-features-redesign`, so GitHub does not have them yet. Bring them into `main` as well, because `main` is what people see when they open the repo link. Run `git status` before you commit: `.gitignore` keeps `.env` files, `target/`, `dist/` and `node_modules/` out.
- [ ] Add your two teammates as collaborators. Each member should make at least a few commits of their own (fix a typo, tweak CSS, add a test) so the history shows all three contributing.
- [x] **Names, IDs and repo URL** are on the report's cover page, and §12 lists the names and IDs too (`docs/report/report.html`).
- [ ] **Group number** (still unknown): replace `[Group No.]` on the report's cover page, then regenerate the PDF (any browser: open `report.html` → Print → Save as PDF, A4, background graphics on) and replace `docs/report/I-ITSM_Project_Report.pdf`. The PDF is being regenerated now with everything except the group number.
- [x] **§12 Team Responsibilities** lists all three members with their IDs and areas. Change it only if your real split differs.
- [ ] **Run the app yourselves at least once** on each member's laptop (`mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `npm run dev`) so everyone can answer questions. Requires Java 17 (`mvn -v` shows which Java Maven uses), Maven, Node 18+.
- [x] `docker compose up --build` works on one machine with Docker Desktop. The first build needs internet to pull the `postgres:16-alpine`, `maven`, `eclipse-temurin`, `node` and `nginx` images.
- [x] **AI key ready** on the machine that took the screenshots (`~/.config/i-itsm/ai.env`; the app showed **AI live**). If you record on another machine, prepare it there the same way. Keep the key outside the project and outside any synced folder (Google Drive, iCloud Desktop & Documents, OneDrive, Dropbox), for example in `~/.config/i-itsm/ai.env` with the one line `ANTHROPIC_API_KEY=...`, and make sure the Anthropic account has some credit. Start the app with `docker compose --env-file ~/.config/i-itsm/ai.env up --build -d`. Never put the key in the project folder, a commit, the report or the video; if an old `.env` in the project still holds a key, delete that line, and revoke the key if the folder was ever synced. A key already exported as `ANTHROPIC_API_KEY` in your shell also switches the AI on and wins over the file. Check that every page after sign-in shows **AI live**. Without the key everything still works, in rules mode (README, "AI features (optional)").
- [ ] Put the **repo link in the shared spreadsheet** the IC circulates.

## B. Record the demo video (15–20 min)
- [ ] Follow one script in `docs/demo/`: `DEMO_SCRIPT_v3.md` (a story, then how it works) or `DEMO_SCRIPT.md` (v2); all three members on camera; three workflows shown; each member explains a module.
- [ ] **Fresh start** just before recording: `docker compose down -v` (deletes the old data, so the first incident is INC-000001), then `docker compose --env-file ~/.config/i-itsm/ai.env up --build -d`, and wait until `docker compose logs backend` shows "Seed complete".
- [ ] **AI parts:** just before recording, the indicator shows **AI live** (moving wave). If it says Rules mode, its reason tells you what to fix (no key, switched off, today's limit reached, credits ran out, key not accepted, AI not responding).
- [ ] Have the error **screenshot for "screenshot to ticket"** ready: copy the ready-made `docs/demo/assets/payment-error-500.png` to the desktop as `payment-error.png`. On camera, click the screenshot box and choose the file, which works with the browser full screen. Keep the clipboard for the description text only (a file copied in Finder can paste as its icon).
- [ ] Rehearse the AI parts sparingly on recording day: every AI call counts against the daily limit (150 by default). If you show rules mode, switch the AI off from the admin area, then switch it back on.
- [ ] **Redesign check** on the recording laptop: look at the pages in both dark and light mode, and check that text is readable at 1080p. The dot waves (login page, dashboard header) and the AI indicator's wave stay still while the operating system's "reduce motion" setting is on, so turn that setting off if you want them moving on camera.
- [ ] Name the file **`<GroupNo>_OOP_S1_2025-2026_Project.mp4`** (the brief's exact naming convention; it needs the group number).
- [ ] Watch it back once: audio audible, screen readable at 1080p, under 20 min.
- [ ] Upload to the shared folder (link will be shared by the IC) **together with the PDF report** on or before 22.11.2026.

## C. What is already done (verify, don't redo)
| Deliverable (brief §Deliverables) | Where |
|---|---|
| 1 Problem analysis | Report §1 |
| 2 Requirements specification (FR, NFR, roles, business rules) | Report §2 |
| 3 System architecture diagram | Report §3, `docs/diagrams/architecture.png` |
| 4 UML: use case + class (classes, attributes, methods, interfaces, inheritance, associations, composition/aggregation) + state | Report §4, `docs/diagrams/` |
| 5 Technology stack + 9-question selection report | Report §6 |
| 6 Source code in version control | <https://github.com/DEVilish770/i-itsm> (push the latest work first, see A) |
| 7 Database design: ER, schema, relationships, sample data | Report §7, `docs/database/schema.sql`, `sample_data.sql` |
| 8 ≥15 documented test cases (normal, boundary, invalid, exceptions, authorization, business rules) | Report §10 — 88 cases (TC-01–TC-88), 111 automated tests, `mvn test` green |
| 9 Demo video | **you record** — scripts in `docs/demo/` |
| Extra: innovation challenge, incl. the optional AI features with Claude | Report §13.1 and §13.4; README "AI features (optional)" |
| Extra: redesigned dark and light UI, with new screenshots | Report §11, `docs/report/img/` |

## D. Rubric self-check (120 marks)
| Criterion | Marks | Evidence to mention in the video/viva |
|---|---|---|
| System design & architecture | 10 | Layered architecture + event bus; diagrams; design rationale in report §5 |
| Functionality (MVP) | 60 | 3 roles · create/view/update/assign/resolve/close · priority engine · 4 assignment strategies · SLA calc/deadlines/violations/warnings · 3 notification channels · full audit · 8 search filters · reports · 6 intelligent features · PostgreSQL (all of it works without AI) |
| User experience | 10 | Role-specific UI, live suggestions, SLA meters, notification bell, live dashboard; redesigned dark/light UI with plain wording; screenshot and chat reporting, plain-English search, friendly updates; AI mode indicator on every page after sign-in |
| Technical depth | 15 | JWT security, WebSocket, scheduler, JPA specifications, Docker, DTO boundary, 6 patterns; third-party API integration (official Anthropic Java SDK) behind an Adapter, with JSON-schema structured outputs validated field by field, one bounded retry, a cool-down and a rules fallback |
| Testing & validation | 5 | 111 tests incl. integration through HTTP and AI tests with a fake gateway (mode reasons, daily limit, retry and cool-down, output validation, search whitelist, endpoint security); exception matrix in §9 |
| Innovation | 10 | Real-time dashboard, auto-escalation, SLA risk prediction, similarity detection, Docker deployment, analytics; AI-assisted classification (screenshot to ticket), chatbot-based reporting, plain-English search, plain-language updates (report §13.1, §13.4) |
| Documentation & presentation | 10 | PDF report + README + demo script |

## E. Likely viva questions (prepare 1-minute answers)
1. Why State pattern instead of a `switch` on status? (one place per state; illegal transitions impossible by construction; open/closed principle)
2. Why is `IncidentCategory` an entity and not an enum? (admins add categories at runtime — requirement)
3. Where is inheritance used and where did you deliberately avoid it? (User hierarchy is-a; channels/strategies use interfaces + composition)
4. How does the admin change the assignment strategy without redeploying? (`StrategyRegistry`, `PUT /api/admin/strategies`)
5. What happens if the notification observer throws? (the bus catches and logs the exception, so the other observers still run, and a failure that does not touch the database, such as a WebSocket push, cannot break the request. Be honest about the limit: observers run synchronously inside the service's transaction, so if an observer's database write fails (e.g. saving a `Notification`), Spring marks the transaction rollback-only and the request fails at commit; the incident change and its audit record roll back together. Production fix: write notifications after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`; the WebSocket pushes already work this way through `AfterCommit`) or in their own `REQUIRES_NEW` transaction / an outbox table)
6. How is a P1 escalated? (SLAMonitor: unacknowledged → team lead → admins, each audited)
7. How do you prevent an employee from acting on someone else's incident? (ownership checks in `IncidentService`, three layers of authorization)
8. Why PostgreSQL over MongoDB? (relational data, joins for reports, integrity)
9. How would it scale to 100× users? (report §6.2 Q9)
10. Show me a test that proves a business rule. (`IncidentLifecycleTest.cannotCloseNewIncident`, `SLATrackerTest.resolutionOutcome`)
11. Can the AI create or change an incident by itself? (No. It only returns drafts and filters. The employee submits a draft through the normal `POST /api/incidents` with their own rights, and every AI answer is validated field by field first.)
12. What happens without an API key, or when Claude is down? (Rules mode: each feature service falls back to its own rules method and the indicator shows the reason. One failed call only makes that request fall back; after a 408, 429 or 5xx the adapter retries once, within the feature's time budget. Two failures in a row with no answer from Claude in between switch every feature to rules for a 60-second cool-down, which an admin switching AI on ends at once. No request fails because of the AI.)
13. How do you stop prompt injection, e.g. a screenshot that says "ignore your instructions"? (The system prompts treat user text and images as data, not instructions; the model has no tools and no database access; search filters must be on a whitelist; a person confirms every draft.)
14. Why put Claude behind an `AiGateway` interface? (Adapter pattern: one class knows the SDK, the tests use a fake so they run offline, free and deterministic, and the provider could change by writing one class.)
15. How do you control the cost? (Claude Sonnet 5.5 at low effort, about 1 US cent per call; a daily limit of 150 calls, so a full day costs about US$1.50; credit exhaustion is detected and switches to rules mode.)
16. Are the AI fallbacks a Strategy, and is the plain-language update an observer? (No to both. Each feature service calls `AiModeService.call` and, when it comes back empty, runs its own rules method: a circuit breaker with a fallback, not swappable strategies. The bus still has three observers: `NotificationObserver` saves the reporter's notification with a template line and hands it to `PlainLanguageUpdateService`, a `@Service` that rewrites it after commit on a background thread.)
