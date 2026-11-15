# Submission checklist — deadline **22 Nov 2026** (hard, no extensions)

## A. Things only you can do (do these first)
- [ ] **Create the GitHub repo** (e.g. `i-itsm-oop-project`, private or public as the IC allows) and push:
  ```bash
  cd i-itsm
  git remote add origin https://github.com/<you>/i-itsm-oop-project.git
  git push -u origin main
  ```
  Add your two teammates as collaborators. Each member should make at least a few commits of their own (fix a typo, tweak CSS, add a test) so the history shows all three contributing.
- [ ] **Fill in names / IDs / group number / repo URL** in `docs/report/report.html` (search for `[Group No.]`, `[Member 1`, `[GitHub repository URL]`), then regenerate the PDF (any browser: open `report.html` → Print → Save as PDF, A4, background graphics on). Replace `docs/report/I-ITSM_Project_Report.pdf`.
- [ ] Update **§12 Team Responsibilities** in the report if your real split differs from the proposed one.
- [ ] **Run the app yourselves at least once** on each member's laptop (`mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `npm run dev`) so everyone can answer questions. Requires Java 17+, Maven, Node 18+.
- [ ] Try `docker compose up --build` on one machine with Docker Desktop (needs internet to pull `postgres:16-alpine`, `maven`, `node`, `nginx` images).
- [ ] Put the **repo link in the shared spreadsheet** the IC circulates.

## B. Record the demo video (15–20 min)
- [ ] Follow `docs/demo/DEMO_SCRIPT.md`; all three members on camera; three workflows shown; each member explains a module.
- [ ] Name the file **`<GroupNo>_OOP_S1_2025-2026_Project.mp4`** (the brief's exact naming convention).
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
| 6 Source code in version control | `i-itsm/` (push to GitHub) |
| 7 Database design: ER, schema, relationships, sample data | Report §7, `docs/database/schema.sql`, `sample_data.sql` |
| 8 ≥15 documented test cases (normal, boundary, invalid, exceptions, authorization, business rules) | Report §10 — 33 cases, 44 automated tests, `mvn test` green |
| 9 Demo video | **you record** — script in `docs/demo/` |

## D. Rubric self-check (120 marks)
| Criterion | Marks | Evidence to mention in the video/viva |
|---|---|---|
| System design & architecture | 10 | Layered architecture + event bus; diagrams; design rationale in report §5 |
| Functionality (MVP) | 60 | 3 roles · create/view/update/assign/resolve/close · priority engine · 4 assignment strategies · SLA calc/deadlines/violations/warnings · 3 notification channels · full audit · 8 search filters · reports · 6 intelligent features · PostgreSQL |
| User experience | 10 | Role-specific UI, live suggestions, SLA meters, notification bell, live dashboard |
| Technical depth | 15 | JWT security, WebSocket, scheduler, JPA specifications, Docker, DTO boundary, 6 patterns |
| Testing & validation | 5 | 44 tests incl. integration through HTTP; exception matrix in §9 |
| Innovation | 10 | Real-time dashboard, auto-escalation, SLA risk prediction, similarity detection, Docker deployment, analytics |
| Documentation & presentation | 10 | 28-page PDF report + README + demo script |

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
