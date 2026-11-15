# I-ITSM: Demo Video Script v3, a story and then how it works

**Team:** Dev Dubey (2025A7IS2413H) · Bheemanathi Rahul Kumar (2025A7PS0011H) · Hitesh MP (2025A7PS0073H)  
**Repository:** <https://github.com/DEVilish770/i-itsm>  
**Upload as:** `<GroupNo>_OOP_S1_2025-2026_Project.mp4`, with the PDF report, on or before 22.11.2026.

**The shape.** Part A tells one story: a bad morning at Acme Payments, a made-up company. It shows every required feature and every smart feature working. Part B opens the hood: all three of you explain how what the viewer just saw works, in plain words, starting with a restaurant analogy for the whole system. Each part is timed at 9:30, so the video ends at about 19 minutes, inside the brief's 15–20.

**Who plays whom.** Dev plays the IT manager (the `admin` tab). Rahul plays Arjun, the technician (the `tech.arjun` tab). Hitesh plays the employees Raj and Meera (the `emp.raj` and `emp.meera` tabs). In Part B each of you explains the part you built: Dev the design and the "brain", Rahul the backend, Hitesh the website, the tests and Docker.

> This assumes Dev, Rahul and Hitesh are Members 1, 2 and 3 in section 12 of the report. If your real split is different, swap the names in Scene 0 and Part B, so everyone explains their own work.

---

## Before recording (15 minutes)
1. **Tests:** `cd backend && mvn test` should end with `Tests run: 53, Failures: 0`. It needs Java 17. On a Mac whose default Java is 11, run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test`.
2. **Fresh start:** in the project folder run `docker compose down -v` (wipes old data, so the first incident is INC-000001), then `docker compose up --build`. Wait for "Seed complete" in the backend log, then open `localhost:5173`.
3. **Four tabs, each typed fresh** (never duplicate a tab, because a copy shares the login): `emp.raj`, `emp.meera`, `tech.arjun` and `admin`, with the demo password from the README.
4. **Demo SLA for P1** (`admin` → *Services, categories, SLA*): response 5 min, resolution 9 min, warning at 45 %. Save. This makes the "SLA at risk" warning appear on camera about 5 minutes after the P1 is created.
5. **Keep open:** `docs/diagrams/architecture.png`, the ER diagram, and a terminal in the project folder.
6. **Record Part A in one take.** The clocks keep running if you pause. Part B can be recorded separately.
7. **Recording:** OBS Studio (free) at 1080p, browser full screen, webcam bubble bottom-right. Mic check, notifications off. Detail pages don't refresh themselves, so reload before pointing at a change.

---

## Part A: the story, "A bad morning at Acme Payments" (9:30)

### Scene 0 · Meet the team (0:00–1:00) · all
*Screen: the login page.*

- **Dev:** "Hi, I'm Dev Dubey, 2025A7IS2413H. I designed the classes and the rules behind them. In today's story I'm the IT manager."
- **Rahul:** "I'm Bheemanathi Rahul Kumar, 2025A7PS0011H. I built the backend, the part that does the real work. Today I'm Arjun, the technician who fixes things."
- **Hitesh:** "I'm Hitesh MP, 2025A7PS0073H. I built the website you'll see, and the tests. Today I'm Raj and Meera, two employees having a bad morning."
- **Dev:** "At Acme Payments, IT problems used to arrive by phone and email. Nobody knew who was handling what, urgent problems waited behind small ones, and deadlines were missed. Here's the same morning with I-ITSM."

### Scene 1 · 9:00 AM, the payment page goes down (1:00–3:00) · Workflow 1
*Hitesh, as Raj, in the `emp.raj` tab.*

1. Show the *Register* form for two seconds: "New staff can sign themselves up." Then log in as `emp.raj`.
2. New incident: service **Payment Application**, title "Payment application unavailable", description "Customers cannot complete checkout, payment page shows 500 error". While typing, point at the suggestion box: "It has already guessed the category, APPLICATION, and it's checking for similar reports. There are none yet."
3. Impact CRITICAL, urgency HIGH, 400 users. **Submit.** It becomes **INC-000001**.
4. On the detail page: "It's a P1, the top priority, worked out from how bad it is, how urgent it is and how important the service is. It already has deadlines, and it's already assigned to Arjun."

*Dev, in the `admin` tab, for five seconds:* "It appeared on my dashboard by itself. I didn't refresh anything."

*Rahul, as Arjun, in the `tech.arjun` tab:* *My queue* → open the P1 → **Acknowledge**. "Got it. Acknowledging now, so the response deadline is met." **Do this within 2 minutes of the Submit.**

*Hitesh, back in the `emp.raj` tab, opens the bell:* "Three notifications: created, assigned to Arjun, acknowledged. I didn't have to chase anyone."

### Scene 2 · 9:05, everyone notices (3:00–4:15) · Workflow 3 begins
*Hitesh, as Meera, in the `emp.meera` tab.*

1. Report on **Payment Application**: title "Payment transaction failing", description "Unable to make payment, checkout error 500", impact MEDIUM, urgency MEDIUM, 1 user. Type it exactly.
2. "It's warning me this is probably the same problem as INC-000001." Tick *submit anyway*. It becomes **INC-000002**, linked to the first one. "So Arjun sees one problem, not two."

*Hitesh, as Raj, in the `emp.raj` tab:*

3. Report on **Single Sign-On**: title "SSO login completely down", description "MFA page times out", impact CRITICAL, urgency CRITICAL, 3000 users. It becomes **INC-000003**: a P1, category AUTHENTICATION, assigned to Priya.

- **Dev:** "Priya is stuck in a meeting and doesn't respond. Keep an eye on this one." (Nobody touches INC-000003 from here on.)

### Scene 3 · 9:06, Arjun gets to work (4:15–6:00) · Workflow 2 begins
*Rahul, as Arjun, in the `tech.arjun` tab.*

1. *My queue*: "My queue shows my workload, with the P1 at the top." Open INC-000001 → **Start investigation**.
2. **Request information**: "Which browser? Is it all customers?" The status becomes PENDING_INFO.

*Hitesh, as Raj:* the bell shows "More information needed". Reply: "All customers, every browser."

*Rahul:* reload. The status is back to IN_PROGRESS, with the note thread. "The whole conversation stays on the ticket."

### Scene 4 · 9:08, the manager tunes the system (6:00–7:15)
*Dev, in the `admin` tab → Services, categories, SLA.*

1. Add a service **Desk Phones** (criticality LOW) and a category **VOIP** with the keywords "phone, dial, voip, call". In *Users & technicians*, give **Rahul** the VOIP skill. "A new kind of problem and a new specialist, and no programmer needed."
2. Back on the dashboard, about 2 minutes after the SSO report, the stream shows **ESCALATED**. "Priya never responded, so after two minutes the system handed her ticket to the team lead, Arjun. Nobody had to notice it by hand."

*Hitesh, as Meera:* report "Desk phone cannot dial" on **Desk Phones** (impact LOW, urgency MEDIUM). It's classified VOIP and assigned to the technician Rahul. **Dev:** "It used the keywords and the skill I added a minute ago."

### Scene 5 · 9:10, beating the clock (7:15–8:30) · Workflow 2 ends
*Rahul, as Arjun, in the `tech.arjun` tab.*

1. Open the bell: "SLA at risk" (it arrives about 5 minutes after INC-000001 was created). "It warns me before the deadline, not after."
2. **Resolve** INC-000001: summary "Restarted payment gateway pods", root cause "expired TLS certificate".

*Dev, in the `admin` tab:* open **INC-000002**, Meera's report. "Look: the fix Arjun just wrote already shows up here as a suggestion. Solve a problem once, and every similar ticket learns the answer."

*Hitesh, as Raj, in the `emp.raj` tab:* the "resolved" notification → **Verify fix** → **Feedback 5★** → **Close**. "And here's the full history: every step, who did it, and when."

### Scene 6 · 9:15, the manager's view (8:30–9:30)
*Dev, in the `admin` tab.*

1. Switch the assignment strategy to **ROUND_ROBIN**: "I can change how work is shared out, live, without restarting anything."
2. **Search**: filter by status, priority, category, service and SLA status.
3. **Reports**: MTTA (time to respond), MTTR (time to fix), SLA compliance, incidents by service and category, technician workload. End on the **Audit log** tab.
4. "One bad morning, and nothing was lost, nobody chased anyone, and every deadline was watched. Now let's show you how it works."

---

## Part B: how it works (9:30)

### B1 · The restaurant (9:30–11:30) · all
*Screen: `docs/diagrams/architecture.png`. Point at each box as it's named.*

- **Dev:** "The easiest way to picture I-ITSM is a restaurant."
- **Hitesh:** "The dining room, with its menu, is the **frontend**: the website you just watched. We built it with React. It shows you things and takes your order, but it never cooks anything itself."
- **Rahul:** "The waiter is the **API**. Every click becomes a written order, called a request, carried to the kitchen, and the answer comes back on the same tray."
- **Rahul:** "The kitchen is the **backend**, written in Java with Spring Boot. All the real work happens here: scoring the priority, picking the technician, watching the deadlines."
- **Dev:** "On the kitchen wall hangs the head chef's rulebook. That's our **domain model**: the Java classes that say what an incident is and which moves are allowed. A chef never serves a dish that isn't cooked, and our system never lets an incident jump from NEW straight to CLOSED."
- **Rahul:** "The pantry is the **database**, PostgreSQL. Everything is stored there and labelled, so nothing is lost even if the kitchen restarts. And the order book, where every order is written down with a time and a name, is our **audit trail**."
- **Hitesh:** "The kitchen bell is the **WebSocket**. Instead of every customer asking 'is my food ready?' again and again, the kitchen rings the moment something changes. That's why the dashboard and the bell updated without anyone pressing refresh."
- **Rahul:** "The wristband you get at the door is the **login token**, a JWT. You show it with every order, so the kitchen knows who you are and what you may order. An employee's wristband doesn't open the manager's office."
- **Hitesh:** "And **Docker** packs the whole restaurant, dining room, kitchen and pantry, into three shipping containers. Anyone can open the exact same restaurant on their laptop with one command."

### B2 · Follow one click (11:30–12:45) · Rahul
*Screen: the architecture diagram.*

"Let's follow Raj's Submit button through the restaurant.
1. The website sends the form, with Raj's wristband, to the kitchen.
2. Security checks the wristband: is it real, and may Raj report incidents?
3. The kitchen's front desk, a controller, passes the order to a service, the cook.
4. The cook follows the rulebook: guess the category, score the priority, look for duplicates, set the deadlines, pick a technician.
5. A repository, the pantry staff, saves it in the database.
6. Then the kitchen calls out 'new incident!'. Three listeners hear it: the audit writes the history, the notifier sends the bell, email and SMS, and the dashboard pushes the change to every manager's screen.
7. The answer goes back to Raj's page. All of that happens in a moment."

### B3 · The website (12:45–14:15) · Hitesh
*Screen: the website, switching between the roles' tabs.*

- "There's one website, but each role sees its own version: employees report and track, technicians get a work queue, and the manager gets the dashboard, the settings and the reports."
- "The buttons aren't decided by the website. It asks the backend 'what's allowed right now?', so it can never show a button for an illegal move, like closing an incident nobody fixed."
- "While Raj typed, the page quietly asked the backend for a category guess and for similar incidents. That's how the suggestions appeared before he pressed Submit."
- "The bell and the live dashboard keep a WebSocket line open, so changes arrive the moment they happen."
- "Each browser tab keeps its own login. That's how four people could work on one laptop in this video."

### B4 · The kitchen: backend, security and data (14:15–16:15) · Rahul
- "The backend has four layers, like a kitchen line: controllers take the orders, services do the work, the domain holds the rules, and repositories talk to the database. Each layer only talks to its neighbour, so a change in one place doesn't break the others."
- "We never store passwords. We store a scrambled fingerprint made with BCrypt, which can't be turned back into the password."
- "Every rule is checked in the backend, not just on the screen." *Optional, 20 seconds:* in the `emp.meera` tab, open `localhost:5173/incidents/1`, which is Raj's incident: "You can only view incidents you reported." Then, in the terminal, try to close Raj's SSO ticket straight through the API:
  ```bash
  T=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"emp.raj","password":"password123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
  curl -i -X POST localhost:8080/api/incidents/3/close -H "Authorization: Bearer $T"
  ```
  The answer is `409` with `INVALID_STATE_TRANSITION`: "Even without the website, nobody can close an incident that was never fixed."
- "The database keeps users, incidents, notes, the audit trail, notifications, services, categories and SLA policies in related tables." Show the ER diagram for five seconds.
- "Notifications use the **Observer** pattern: the incident only announces what happened, and listeners decide what to do. Adding WhatsApp would be one new class, without touching the incident."
- "A background timer checks every open incident every 10 seconds. That's what warned Arjun before his deadline, and what moved Priya's ticket to Arjun. In this demo the timers are fast; normally escalation waits 10 minutes."

### B5 · The brain: design and the smart features (16:15–18:15) · Dev
- "Three ideas from our OOP course do most of the work."
- "**Inheritance:** an Employee, a Technician and an Administrator are all Users. They share the login details, and each adds what's special about it, like a technician's skills and workload."
- "**The State pattern:** each status, like NEW or IN_PROGRESS, is a small object that knows its legal next moves. Asking a NEW incident to close simply throws an error. That's the 409 you just saw, and it's also where the website's buttons come from."
- "**The Strategy pattern:** how we score priority and how we pick a technician are plug-ins. There are two ways to score priority and four ways to assign work, and in the story the manager switched to round robin live."
- "Now the smart parts, in plain words."
  - "**Priority:** impact times urgency times how important the service is, each from 1 to 4, plus a bonus when many users are hit. Raj's outage scored 4 × 3 × 4 = 48, plus 8 for 400 users, so 56. Anything from 36 up is a P1."
  - "**Category:** every category has keywords. A keyword in the title scores 2 points, in the description 1, and the category with the most points wins."
  - "**Technician:** every available technician gets a score out of 110: 40 for the right skill, up to 25 for free time, up to 20 for experience, up to 15 for knowing that service, and 10 for a team lead on an urgent ticket. The highest score wins, which is why Arjun got the payment outage."
  - "**Duplicates:** we turn both reports into sets of key words, then divide the words they share by all the different words in both. Meera's report shared 27 %, above our 25 % line, so it was flagged and linked."
  - "**Suggested fixes:** when an incident is resolved, similar open incidents are shown its fix. That's what happened to Meera's ticket."
  - "**Deadlines:** every priority has a response deadline and a fix deadline. The system warns before a deadline and escalates when nobody responds."

### B6 · Tested and packed (18:15–19:00) · Hitesh, then all
- **Hitesh:** "We wrote 53 automated tests: normal use, edge cases, bad input, errors, permissions and business rules. All of them pass." *(Optional: show the `mvn test` result for three seconds.)*
- **Hitesh:** "Everything runs in three Docker containers, the website, the backend and the database, started with one command, so it works the same on any laptop."
- **Dev:** "The brief asked how object-oriented design makes an incident system easy to maintain, to extend and to make smart."
- **Rahul:** "Our answer: rules that can change live, a lifecycle that can't be broken, and side effects that simply plug in."
- **Hitesh:** "And every feature is backed by a test. The code is on GitHub at the link on screen. Thanks for watching." *Show the repository link.*

---

## What the story covers

| Required by the brief | Where you show it |
|---|---|
| Three roles, each with its own access | every scene; B3, B4 |
| Registration and login | Scene 1 |
| Create, view, update, assign, resolve and close an incident | Scenes 1, 3, 5 |
| Classification and priority | Scene 1, Scene 4; B5 |
| SLA deadlines and warnings | Scene 1, Scene 5; B4 |
| Assignment strategies, switchable live | Scene 1, Scene 4, Scene 6 |
| Notifications on more than one channel | Scenes 1, 3, 5; B4 |
| Audit history | Scenes 5, 6 |
| Search with at least five filters, reports, dashboard | Scene 6 |
| Workflow 1: report → prioritise → assign → notify | Scene 1 |
| Workflow 2: investigate → SLA watched → resolve → notify | Scenes 3, 5 |
| Workflow 3: duplicates → linked → escalated | Scenes 2, 4 |

| Smart feature | Where |
|---|---|
| Automatic category | Scenes 1, 4 |
| Duplicate detection and linking | Scene 2 |
| Smart technician choice | Scene 1; B5 |
| SLA risk warning | Scene 5 |
| Automatic escalation | Scene 4 |
| Suggested fixes from history | Scene 5 |
| Live dashboard | Scene 1; B1 |
| Analytics: MTTA, MTTR, SLA compliance | Scene 6 |
| One-command Docker deployment | B1, B6 |

## Timing budget
| Part | Minutes |
|---|---|
| Part A: Scenes 0–6 | 9.5 |
| Part B: B1–B6 | 9.5 |
| **Total** | **19** |

## If something goes wrong
- "No technician available": that technician is at capacity, so raise `maxWorkload` in *Users & technicians*.
- A duplicate warning blocks a report you wanted: tick *submit anyway*.
- The live badge stays on "connecting…": reload the tab once.
- The escalation needs 2 full minutes with no change to INC-000003. Fill the wait with Scene 4's admin steps.
- Too slow and the P1 missed its deadline? Run `docker compose down -v && docker compose up`, log in again in all four tabs, and restart Part A.
- Don't over-claim: email and SMS are simulated, and "linked" means similar words on the same service.
