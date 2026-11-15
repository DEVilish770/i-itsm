# I-ITSM: Demo Video Script, v2 (revised 10 Nov 2026)

**What changed from v1.** The video now follows the brief's own arc: Analyze → Model → Design → Implement → Test → Evaluate → Defend. Every design choice is justified and defended, and the tools we used are named openly. Every demo step was checked against the code: v1 had several steps that would have failed on camera, and the timings below are set so the SLA warning and the escalation land when the script needs them.

**File name for upload:** `<GroupNo>_OOP_S1_2025-2026_Project.mp4`, exactly as the brief writes it. Upload it with the PDF report on or before 22.11.2026 (our own cutoff: 09:00 that day).

**Rules from the brief:** a recorded demo of the working application, 15–20 minutes, involving all members · while it records, each member introduces themself and explains at least one module · at least three complete workflows.

**Format:** a screen recording of one laptop that runs the app, with the speaker's webcam in a small bubble in the **bottom-right corner**. On the dashboard and most pages that corner is empty space, while the bell sits top-right and the logged-in user shows bottom-left, so both stay visible. The webcam shows clearly that every member took part.

---

## Before recording (15 minutes)
1. **Run the tests once:** `mvn test` in `backend/` should end with `Tests run: 53, Failures: 0, Errors: 0` and `BUILD SUCCESS` (checked on 10 Nov, after the fixes).
2. **Start the app with Docker.** Open the Docker app and wait until the engine is running. In the project folder, run `docker compose down -v` (it wipes old data, so the first incident is INC-000001), then `docker compose up --build`. Wait for "Seed complete" in the backend log, then open `localhost:5173`. It runs on Indian time with demo-speed timers. Backup plan if Docker misbehaves: the dev setup (`mvn spring-boot:run -Dspring-boot.run.profiles=dev` in `backend/`, plus `npm install && npm run dev` in `frontend/`) shows exactly the same app.
3. **Four logins in four fresh tabs:** `emp.raj`, `tech.arjun`, `admin` and `emp.meera` (password `password123`). Each tab keeps its own login, and closing a tab logs it out. Type the address into each new tab and don't duplicate a tab, because a duplicate copies the login.
4. **Demo SLA for P1** (admin → *Services, categories, SLA*): **response 5 min, resolution 9 min, warning at 45 %.** Save. The warning fires when 45 % of the window is left, so about 5 minutes after the incident is created. The brief's own table gives P1 30 minutes to resolve; we shorten it only so the warning shows up on camera.
5. **Keep open:** `docs/diagrams/*.png` (architecture, use case, class, state), a terminal, and the IDE with `Incident.java`, `IncidentStates.java`, `StrategyRegistry.java`, `IncidentEventBus.java`, `NotificationObserver.java` and `NotificationChannel.java`. Also report §5.1 (OOP table) and §9 (exceptions).
6. **The clocks keep running when you pause the recording.** Record Segments 2 and 3 in one go, so the P1 is resolved before its 9-minute mark.
7. Record at 1080p with the browser full screen, and the webcam bubble bottom-right (OBS Studio, free, does screen + webcam in one recording). Check the mic and turn off notifications. Detail pages don't refresh themselves, so reload before pointing at a change.

---

## Segment 0: Introduction and Analyze (all, 1.5 min)
- **Each member:** name, ID and the module they owned (Member 1: domain model and OOP design · Member 2: backend, API, persistence, security · Member 3: frontend, dashboard, testing, documentation).
- **The problem in one breath:** "Hundreds of IT incidents a day, handled by hand, cause the eight problems the brief lists: slow responses, wrong priorities, poor technician allocation, missed SLAs, no visibility, duplicate reports, incomplete history and recurring problems. I-ITSM answers each one with an object-oriented design. That's what we'll show you."
- **Stakeholders:** employees who report, technicians who fix, and administrators and IT managers who run the service.

## Segment 1: Model and Design: why we built it this way (Member 1, 2 min)
For each decision: the problem, our choice, why, and the alternative we rejected.

- **A full-stack web app (the brief's option C).** All three roles work at desks and need no install. One backend holds every rule, so no screen can skip a step, and each role gets its own interface, as the brief asks. A mobile app would mean a second codebase for staff who work on laptops. A desktop app would have to be installed and updated on every machine.
- **Java 17 + Spring Boot.** Java is where the course's OOP lives: interfaces, abstract classes, strong typing. Spring's dependency injection finds every strategy, observer and channel automatically, which is exactly what makes our patterns pluggable. We rejected Python/FastAPI and Node because looser typing makes the design less explicit.
- **PostgreSQL.** Incident data is relational (reporter, technician, service, category, notes, audit) and needs reliable transactions, because an SLA breach must never be lost. We rejected MongoDB because the workload and service reports need joins.
- **One layered app with an event bus inside**, not microservices: the right size for three people. The bus gives us loose coupling without running a message broker.
- **The model:** the use case diagram (three actors), then the class diagram. Point to **inheritance** only where something truly is a kind of something else (Employee, Technician and Administrator are Users). Then **composition** (an Incident owns its SLA tracker, notes and resolution), **aggregation** (technicians and their skill categories) and the **interfaces** (IncidentState, PriorityStrategy, AssignmentStrategy, IncidentObserver, NotificationChannel). Also say **where we chose not to inherit**: categories are data, not subclasses, so an admin can add one without code.
- **Tools, named openly:** "The brief allows packages and AI tools. We used [say what you used, for example AI help for boilerplate and diagrams]. Every rule is pinned by an automated test, and we reviewed the code against the brief line by line."

## Segment 2: Implement, workflow 1: report → prioritise → assign → notify (Member 1, 4 min)
*Screen: the `emp.raj` tab.*

1. **Register, then log in.** Show the *Register as employee* form for two seconds (the brief requires registration), then log in as `emp.raj`. One sentence: passwords are hashed with BCrypt, and login returns a JWT.
2. **Report the brief's own scenario.** Service **Payment Application**, title "Payment application unavailable", description "Customers cannot complete checkout, payment page shows 500 error". Pause on the live **category suggestion** (feature F: APPLICATION, matched on whole words) and the empty similar-incidents box. Impact CRITICAL, urgency HIGH, 400 users. Submit. It becomes **INC-000001**.
3. **The detail page.** The rationale reads impact CRITICAL(4) × urgency HIGH(3) × criticality MISSION_CRITICAL(4) = 48, plus an 8-point users bonus, so the score is 56 → **P1**. **Problem → choice → why:** "Priority rules differ between organisations, so priority is a **Strategy**: two interchangeable implementations behind one interface, and the admin swaps them live." Then the SLA meter with its deadlines, the technician (Arjun, picked by the smart recommendation strategy, feature D) and the audit timeline. Point at the buttons: "This page asks the incident's current state which moves are legal, so the screen never offers an illegal one."
4. **Acknowledge now.** Switch to the `tech.arjun` tab → *My queue* → open the P1 → **Acknowledge**. Do it within 2 minutes of submitting. A P1 that nobody acknowledges escalates after 2 minutes, and workflow 3 shows that on purpose.
5. **The bell** (back in the `emp.raj` tab): "Incident created", "assigned to Arjun Mehta", "acknowledged". **Observer + Adapter:** "The incident never calls email or SMS. It publishes an event, and each channel is one class behind a NotificationChannel interface. Adding WhatsApp would be one new class."
6. **Duplicate detection (feature A).** Report on the same service: title "Payment transaction failing", description "Unable to make payment, checkout error 500", impact MEDIUM, urgency MEDIUM, 1 user. Type it exactly: it scores 27 % against a 25 % threshold, so the warning "possible duplicate of INC-000001" appears. Click *submit anyway*. It becomes **INC-000002**, linked as related, and stays a P3, so it won't escalate.
7. **Code moment (1 min), the State pattern.** `Incident.java` has no `setStatus`; `acknowledge()` hands the call to the current state object. **Problem → choice → why:** "The brief says a NEW incident must never jump to CLOSED. With a switch on the status, that rule would be scattered and easy to break. Here each state allows only its legal moves, and anything else throws `InvalidStateTransitionException`. The buttons you just saw come from the same state object. Member 2 will prove the backend refuses an illegal move even without the screen."

## Segment 3: Implement, workflow 2: investigate → SLA → resolve → learn → close (Member 2, 4 min)
*Screen: the `tech.arjun` tab.*

1. **My queue:** capacity and workload, with the P1 at the top, already acknowledged, so the response SLA is met and stamped. Open it → **Start investigation**.
2. **Request information** ("Which browser? All customers?"). The status becomes PENDING_INFO, and the `emp.raj` bell shows "More information needed". Reply as Raj. Back as Arjun (reload): IN_PROGRESS, with the note thread visible.
3. **The rules hold even without the screen.**
    - **409, the State pattern:** the screen never offers *Close* on an in-progress incident, so try it straight against the API as Raj, in the terminal:
      ```bash
      T=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"emp.raj","password":"password123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
      curl -i -X POST localhost:8080/api/incidents/1/close -H "Authorization: Bearer $T"
      ```
      The answer is `409` with `INVALID_STATE_TRANSITION`.
    - **403, ownership:** in the `emp.meera` tab, open `localhost:5173/incidents/1` (Raj's incident). The page shows "You can only view incidents you reported" (`403 UNAUTHORIZED_OPERATION`). A technician who isn't assigned gets no action buttons, and the backend refuses them too.
    - One sentence: "These checks live in the service layer, so no screen and no API call can get round them."
4. **SLA risk detection (feature E):** about 5 minutes after the P1 was created, the **SLA warning** arrives while Arjun is still working. Arjun gets an in-app alert and an SMS, and the admins an in-app alert. Show them in the `admin` tab under *Audit log* → *All notifications*. Resolve well before the 9-minute mark, so the SLA ends as MET.
5. **Resolve:** summary "Restarted payment gateway pods", root cause "expired TLS certificate".
6. **It learns from history (feature G).** In the `admin` tab, open **INC-000002**, the duplicate from workflow 1. The *Suggested resolutions from history* panel now shows the fix just entered on INC-000001. "Resolve one incident, and the similar open one immediately gets the answer."
7. **Close the loop** in the `emp.raj` tab: the "resolved" notification → **Verify fix** → **Feedback 5★** → **Close**. Show the full audit trail: every step with its time, user, old status and new status.
8. **Code moment (1 min):** `IncidentEventBus.publish` loops over the observers (audit, notifications, live dashboard), each independent. `NotificationObserver` holds one routing table that decides who is told what, on which channel. `NotificationChannel` has the email channel as an **Adapter** over an email gateway, and `IncidentRepository` is the **Repository** pattern. Then the ER diagram: "relational, because the reports need joins."

## Segment 4: Implement, workflow 3: correlation, escalation, admin, reports (Member 3, 4 min)
*Screen: the `admin` tab with the live dashboard.*

1. **A second outage.** From the `emp.raj` tab, report on **Single Sign-On**: title "SSO login completely down", description "MFA page times out", impact CRITICAL, urgency CRITICAL, 3000 users. The event stream updates at once: created → classified AUTHENTICATION → assigned to Priya. Score 72 → P1. Nobody acknowledges it, on purpose.
2. **Correlation (features A and B).** From the `emp.meera` tab, report the same thing in her words: title "SSO login down", description "MFA page times out", impact MEDIUM, urgency MEDIUM, 1 user. The duplicate warning appears (88 % similar). Submit anyway: the new incident is linked, and its intelligence panel lists the related open P1 with its similarity.
3. **Admin changes while the escalation clock runs** (*Services, categories, SLA*):
    - Add a service **Desk Phones** (criticality LOW) and a category **VOIP** with keywords "phone, dial, voip, call". In *Users & technicians*, give **Rahul** the VOIP skill.
    - From the `emp.meera` tab, report "Desk phone cannot dial" on Desk Phones (impact LOW, urgency MEDIUM). It is classified VOIP and assigned to Rahul, with no code change.
    - **Then** switch the assignment strategy to **ROUND_ROBIN** and show it takes effect at once, with no redeploy (Strategy again). Order matters: under round robin the VOIP ticket would have gone to Arjun.
    - Point at the SLA policy table: the brief's targets, set per priority. A service's criticality pushes the priority up, so a mission-critical service gets the tighter clock.
4. **Automatic escalation (feature C).** Back on the dashboard: about 2 minutes after the SSO P1 was created, the stream shows **ESCALATED**, "unacknowledged … reassigned to team lead Arjun Mehta". The admins are notified, and the incident carries an "Escalated L1" badge. Say: "The brief's chain runs technician → team lead → system admin → IT manager. We built the first two levels, and each further level is one more rule in the same monitor." If you stay longer, a level-2 alert to the administrators follows 4 minutes later.
5. **Search and reports.** Filter by status, priority, category, service and SLA status (the brief asks for at least five). Then *Reports*: MTTA, MTTR, SLA compliance %, incidents by service and category, and technician workload. Finish on the *Audit log* tab.

## Segment 5: Test and Evaluate (Member 2, 2 min)
1. **Terminal:** `mvn test` → `Tests run: 53, Failures: 0, Errors: 0`. Map the tests to the brief's six kinds: normal operation, boundary, invalid input, exceptions, authorization and business rules. Show one boundary test (`SLATrackerTest.warningThreshold`) and one business rule (`IncidentLifecycleTest.cannotCloseNewIncident`).
2. **Exceptions:** the brief lists 12 bad situations. Show the custom family under `ItsmException` and the matrix in report §9: each situation → its exception → its HTTP status, all through one `GlobalExceptionHandler`.
3. **Security in one breath:** JWT login, BCrypt passwords, role rules on every endpoint, ownership checks in the service layer, validated input, and employees seeing only their own incidents.

## Segment 6: Defend: scale, limits, next steps (Member 3, then all, 1.5 min)
- **It already runs like a real deployment:** show the Docker app's container list for 5 seconds. "Everything you saw runs in three containers (PostgreSQL, the backend and the website), started with one command, `docker compose up`."
- **At 100× the users:** the backend is stateless (JWT), so run several copies behind a load balancer. Add indexes for the report queries. Move the in-process event bus to a message broker (Kafka or RabbitMQ) and send notifications asynchronously. Relay the WebSockets through the broker.
- **Honest limits:** email and SMS are simulated (the brief allows that) · escalation covers two of the four levels · correlation means similarity plus the same service, not root-cause analysis · SLA targets are set per priority, and a service reaches its SLA through its criticality · the SLA clock keeps running while we wait for the employee's reply · one running instance.
- **Next:** ML-based categorisation and priority, a service dependency graph for correlation, per-service SLA overrides, and mobile push through a PWA.
- **Close (all):** "The brief asked how object-oriented thinking makes an incident system maintainable, extensible and intelligent. Our answer: rules that change live behind interfaces (Strategy), a lifecycle that can't be broken (State), side effects that plug in (Observer), and every feature backed by tests." Show the repository link on screen.

---

## Timing budget
| Segment | Who | Minutes |
|---|---|---|
| 0 Introduction and Analyze | all | 1.5 |
| 1 Model and Design | Member 1 | 2 |
| 2 Workflow 1 + State pattern | Member 1 | 4 |
| 3 Workflow 2 + Observer, Adapter, Repository | Member 2 | 4 |
| 4 Workflow 3 + admin + reports | Member 3 | 4 |
| 5 Test and Evaluate | Member 2 | 2 |
| 6 Defend | Member 3 + all | 1.5 |
| **Total** | | **19** |

## Recovery tips
- "No technician available": that technician is at capacity, so raise `maxWorkload` in *Users & technicians*.
- A duplicate blocks a report you wanted: tick *submit anyway*.
- The WebSocket badge stays on "connecting…": reload the tab once.
- The escalation needs 2 full minutes with no change to the incident. *Run SLA monitor now* only runs the check at once; it can't skip the wait, so fill the time with step 3 of Segment 4.
- Recorded too slowly and the P1 breached? Restart and record Segments 2 and 3 again: run `docker compose down -v && docker compose up` (about a minute; `-v` wipes the old incidents so the numbers start again at INC-000001). A restart also signs every tab out, so log in again in all four. To keep logins across restarts, set `JWT_SECRET` (32+ characters) in your shell or in a `.env` file.
- Don't over-claim: there are no Playwright tests, and "correlation" means similarity plus the same service.
