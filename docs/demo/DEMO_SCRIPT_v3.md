# I-ITSM: Demo Video Script v3, a story and then how it works

**Team:** Dev Dubey (2025A7IS2413H) · Bheemanathi Rahul Kumar (2025A7PS0011H) · Hitesh MP (2025A7PS0073H)  
**Repository:** <https://github.com/DEVilish770/i-itsm>  
**Upload as:** `<GroupNo>_OOP_S1_2025-2026_Project.mp4`, with the PDF report, on or before 22.11.2026.

**The shape.** Part A tells one story: a bad morning at Acme Payments, a made-up company. It shows every required feature, every smart feature and the four AI helpers working. Part B opens the hood: all three of you explain how what the viewer just saw works, in plain words, starting with a restaurant analogy for the whole system. Each part is timed at 9:30, so the video ends at about 19 minutes, inside the brief's 15–20.

**The AI helpers.** Four features use Claude, Anthropic's AI (model Claude Sonnet 5.5): a screenshot becomes a ticket, a short chat becomes a ticket, the search understands plain English, and updates are rewritten in everyday words. Each one has a rules fallback, so the story still works with the AI off. Each AI step has a *Rules mode* note for that.

**Who plays whom.** Dev plays the IT manager (the `admin` tab). Rahul plays Arjun, the technician (the `tech.arjun` tab). Hitesh plays the employees Raj and Meera (the `emp.raj` and `emp.meera` tabs). In Part B each of you explains the part you built: Dev the design and the "brain", Rahul the backend and Docker, Hitesh the website and the tests. All three share B6, the AI helper.

> This assumes Dev, Rahul and Hitesh are Members 1, 2 and 3 in section 12 of the report. If your real split is different, swap the names in Scene 0 and Part B, so everyone explains their own work. If one of you built the AI helpers, give that person all of B6.

---

## Before recording (15 minutes)
1. **Tests:** `cd backend && mvn test` should end with `Tests run: 111, Failures: 0`. It needs Java 17. On a Mac whose default Java is 11, run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn test`.
2. **AI key, outside the project:** the private file `~/.config/i-itsm/ai.env` holds one line, `ANTHROPIC_API_KEY=` followed by the key. Never keep the key in the project folder or in any folder that Google Drive, iCloud, OneDrive or Dropbox syncs, because a synced folder uploads it. A key already exported in your terminal wins over the file (`echo ${ANTHROPIC_API_KEY:+set}` prints `set` if there is one). Without a key, the app starts in rules mode.
3. **Fresh start:** in the project folder run `docker compose down -v` (wipes old data, so the first incident is INC-000001), then `docker compose --env-file ~/.config/i-itsm/ai.env up --build -d`. Wait until `docker compose logs backend` shows "Seed complete", then open `localhost:5173`.
4. **AI check:** the top bar should show a small moving wave and **AI live**. A flat line and "Rules mode" always comes with its reason: no key, switched off, today's limit reached, credits ran out, key not accepted, or AI not responding. Each AI use costs about 1 US cent, so rehearsing is cheap, but look at the indicator again just before the real take, in case rehearsals used up today's limit.
5. **Four tabs, each typed fresh** (never duplicate a tab, because a copy shares the login): `emp.raj`, `emp.meera`, `tech.arjun` and `admin`, with the demo password from the README. Leave `emp.raj` on the login page, because Raj logs in on camera.
6. **Demo SLA for P1** (`admin` → *Settings* → *Deadlines by priority*, row Urgent P1): Respond within 5, Fix within 9, Warn when time left is below 45. Save. This makes the "SLA warning" appear in Arjun's bell about 5 minutes after the P1 is created. Then leave the `admin` tab on the dashboard, so Raj's report in Scene 1 appears there by itself.
7. **For Scene 1, a ready-made screenshot:** copy `docs/demo/assets/payment-error-500.png` to the desktop as `payment-error.png`. It shows "500 Internal Server Error" at `checkout.acme-payments.example/pay/order/48213`, with the reference `PAY-GW-500 · upstream timeout`. Raj clicks the screenshot box and chooses the file in the file chooser, which works with the browser full screen. Drag it in only if a Finder window sits beside the browser, and don't paste the picture: a file copied in Finder can paste as its icon. Keep the clipboard for the description: just before you start, copy `Customers cannot complete checkout, payment page shows 500 error`, so Raj can paste it instead of typing it.
8. **Keep open:** `docs/diagrams/architecture.png`, the ER diagram, and a terminal in the project folder.
9. **Record Part A in one take.** The clocks keep running if you pause. Part B can be recorded separately. Do one full dry run with the AI on first: its wording changes a little every time, so read what it says rather than learning it. Part A ends with the AI switched off, so switch it back on before any retake.
10. **Recording:** OBS Studio (free) at 1080p, browser full screen, webcam bubble bottom-right. Mic check, notifications off. Detail pages don't refresh themselves, so reload before pointing at a change.

---

## Part A: the story, "A bad morning at Acme Payments" (9:30)

### Scene 0 · Meet the team (0:00–0:55) · all
*Screen: the login page.*

- **Dev:** "Hi, I'm Dev Dubey, 2025A7IS2413H. I designed the classes and the rules behind them. In today's story I'm the IT manager."
- **Rahul:** "I'm Bheemanathi Rahul Kumar, 2025A7PS0011H. I built the backend, the part that does the real work. Today I'm Arjun, the technician who fixes things."
- **Hitesh:** "I'm Hitesh MP, 2025A7PS0073H. I built the website you'll see, and the tests. Today I'm Raj and Meera, two employees having a bad morning."
- **Dev:** "At Acme Payments, IT problems used to arrive by phone and email. Nobody knew who was handling what, urgent problems waited behind small ones, and deadlines were missed. Here's the same morning with I-ITSM and its new AI helper."

### Scene 1 · 9:00 AM, the payment page goes down (0:55–2:45) · Workflow 1
*Hitesh, as Raj, in the `emp.raj` tab. Aim to press Send report at about 1:45.*

1. Show the *Register as employee* tab for two seconds: "New staff can sign themselves up." Then log in as `emp.raj`.
2. **Report a problem** → click the screenshot box and choose `payment-error.png` on the desktop: "I don't want to type a long report, so I'll just drop in a screenshot of what I see." While it reads, point at the top bar: "See 'AI live', with the little moving wave? That means the AI helper is on." Then: "It read the error page and filled in the whole form. Each field it filled has a small AI tag." *Rules mode:* the top bar shows a flat line, **Rules mode** and the reason, and the screenshot box says the AI can't fill in the form. Say "The AI is off, so I'll type it, and the built-in rules still sort it", and type the step 3 values by hand.
3. **Check it.** "I'm the one sending this, so I check it first. I'll put the title and description in my own words, and add how many users are hit." Whatever the AI wrote, make the form say exactly: title "Payment application unavailable" (type it), description "Customers cannot complete checkout, payment page shows 500 error" (paste it), service **Payment Application**, category **APPLICATION**, impact **Critical**, urgency **High**, and **400** people affected. Meanwhile, point at *Similar open reports*: "It's also checking for similar reports. There are none yet."
   *Why exactly these: Meera's report in Scene 2 is matched against this title and description (other wordings can drop the match below the 25 % line), the category sends it to Arjun, and impact and urgency give the score of 56 explained in B5.*
4. **Send report.** It becomes **INC-000001**.
5. On the detail page: "It's marked Urgent, with the technical name, P1, in small letters. That was worked out from how bad it is, how urgent it is and how important the service is. It already shows the time left, and it's already assigned to Arjun."

*Dev, in the `admin` tab, for ten seconds:* "It appeared on my dashboard by itself. I didn't refresh anything." Move the mouse over the header, and the field of dots lifts toward it: "We kept the screen calm on purpose. Nobody opens this app on a good day."

*Rahul, as Arjun, in the `tech.arjun` tab:* *My queue* → open the urgent one → **Acknowledge**. "Got it. Acknowledging now, so the response deadline is met." **Do this within 2 minutes of Send report.**

*Hitesh, back in the `emp.raj` tab, opens the bell:* "Three updates: my report arrived, Arjun has it, and he's on it. The AI writes them in everyday words." The AI wording replaces the template a few seconds after each change, so read the newest one out loud once its tag says **AI** (usually 2–5 seconds; the open bell updates by itself, or close and reopen it). It will say something like "Arjun Mehta from IT support is now working on your payment problem (INC-000001)." If it still says Rules after about 10 seconds, read the template instead (in rules mode the template is final, so read it at once). Then: "I didn't have to chase anyone."

### Scene 2 · 9:05, everyone notices (2:45–3:50) · Workflow 3 begins
*Hitesh, as Meera, in the `emp.meera` tab, on the normal form (no screenshot, no chat).*

1. Report on **Payment Application**: title "Payment transaction failing", description "Unable to make payment, checkout error 500", impact Medium, urgency Medium, 1 person affected. Type it exactly, then **Send report**.
2. "It's warning me this is probably the same problem as INC-000001." Click *It's a different problem — submit anyway*. It becomes **INC-000002**, linked to the first one. "So Arjun sees one problem, not two."

*Hitesh, as Raj, in the `emp.raj` tab, also on the normal form:*

3. Report on **Single Sign-On**: title "SSO login completely down", description "MFA page times out", impact Critical, urgency Critical, 3000 people affected. While typing: "Typed by hand, the rules still guess the category from the words: AUTHENTICATION, because of SSO, login and MFA." It becomes **INC-000003**: Urgent (P1), assigned to Priya.

- **Dev:** "Priya is stuck in a meeting and doesn't respond. Keep an eye on this one." (Nobody touches INC-000003 from here on.)

### Scene 3 · 9:06, Arjun gets to work (3:50–4:45) · Workflow 2 begins
*Rahul, as Arjun, in the `tech.arjun` tab.*

1. *My queue*: "My queue shows my workload, with the urgent one at the top." Open INC-000001 → **Start investigation**.
2. **Ask the reporter**: "Which browser? Is it all customers?" The status becomes PENDING_INFO.

*Hitesh, as Raj:* the bell has a friendly note asking for more information (its AI tag appears a few seconds later; in rules mode it stays the template, tagged Rules). Click **Reply with information**: "All customers, every browser."

*Rahul:* reload. The status is back to IN_PROGRESS, with the note thread. "The whole conversation stays on the ticket."

### Scene 4 · 9:08, the manager tunes the system (4:45–6:55)
*Dev, in the `admin` tab → Settings.*

1. Add a service **Desk Phones** (criticality low) and a category **VOIP** with the keywords "phone, dial, voip, call". In *People*, give **Rahul Verma** the VOIP skill. "A new kind of problem and a new specialist, and no programmer needed."

*Hitesh, as Meera, in the `emp.meera` tab:* **Report a problem** → **Describe it in a chat instead**. A side sheet opens.

2. Type: "My desk phone can't dial out." The AI asks one short question at a time, three at most. Answer in a few words, for example "Just mine", "Since this morning", "I can use my mobile for now." "It's like texting someone at the help desk."
3. It shows **Here's what I'll report**. Check that it says **Desk Phones** (category VOIP, or "The system will decide"), then **Submit**. It's classified VOIP and assigned to the technician Rahul Verma. *Rules mode:* the sheet says "AI is off: what you write here goes into the form." After the first message, click **Back to the form**, choose Desk Phones and press **Send report**: the keywords still make it VOIP, so it still goes to Rahul Verma.

- **Dev:** "The service I added a minute ago was already one of its choices, and the skill I gave Rahul Verma sent the ticket to him."

*Dev, back in the `admin` tab:*

4. In *Settings* → *How work is shared*, set *Choosing a technician* to **Round robin**: "I can change how work is shared out, live, without restarting anything." (Only after Meera's ticket: under round robin it would have gone to someone else.)
5. Open the bell in the `admin` tab: **Escalation: INC-000003** (it fires about 2 minutes after the SSO report; *Live activity* on the dashboard lists only what arrives while the dashboard is open). Open INC-000003 for a second to show "Escalated, level 1". "Priya never responded, so after two minutes the system handed her ticket to the team lead, Arjun. Nobody had to notice it by hand."

**Start Scene 5 only once 5 minutes 10 seconds have passed since Raj's Send report**, so the warning is already waiting in Arjun's bell.

### Scene 5 · 9:10, beating the clock (6:55–8:00) · Workflow 2 ends
*Rahul, as Arjun, in the `tech.arjun` tab.*

1. Open the bell: "SLA warning: INC-000001", with only a few minutes left (it arrives about 5 minutes after INC-000001 was created). "It warns me before the deadline, not after."
2. **Resolve** INC-000001: *How was it fixed?* "Restarted payment gateway pods", *Root cause* "expired TLS certificate", **Send**.

*Dev, in the `admin` tab:* open **INC-000002**, Meera's report. "Look: the fix Arjun just wrote already shows up here, under Fixes that worked before. Solve a problem once, and every similar ticket learns the answer."

*Hitesh, as Raj, in the `emp.raj` tab:* open the bell and read the "fixed" update out loud once its tag says AI (in rules mode, the template tagged Rules). It explains the fix without the jargon, something like "IT support restarted the payment system; please check that it works." Then **Verify fix** → **Give feedback** (rating 5, Send) → **Close** (Send). "And here's the full history: every step, who did it, and when."

### Scene 6 · 9:15, the manager's view (8:00–9:30)
*Dev, in the `admin` tab.*

1. **Plain-English search:** click the search line in the top bar (or press ⌘K) and type "urgent login problems". "I can just ask in normal words." It shows how it understood you as filter words, such as *Urgent priority* and *Single Sign-On*, then the result: the SSO outage that escalated. Remove the login words (the service, the category or a quoted word, whichever it picked) until only *Urgent priority* is left, and the list grows to every urgent ticket, including Raj's payment outage from this morning. "If it gets me wrong, I just remove a word." *Rules mode:* the palette says "AI is off: search matches the words you type.", so type **SSO** instead (the whole sentence would match nothing) and skip removing words.
2. *Open the full list →* opens the *Incidents* page; two seconds there: "The same filters work by hand too: state, priority, deadline, category and service."
3. **Reports**: Time to respond (MTTA), Time to fix (MTTR), Met their deadline (SLA compliance), incidents by service and category, technician workload. Read the first sentence of **In plain words** out loud: "The AI sums up all the figures for me." (In rules mode it's the same figures in fixed sentences, tagged Rules: "The built-in rules sum up the figures.") Then **Audit log** → **All notifications**, five seconds: point at INC-000001's IN_APP, EMAIL and SMS rows. "Every update went out on more than one channel."
4. **Switch the AI off:** in *Settings*, turn off **AI assistance**. The top bar shows a flat line and **Rules mode**, switched off. "This is exactly what the evaluators see without a key, or if our credits run out: everything still works." *Rules mode:* skip the switch and say: "Right now we're in rules mode, which is exactly what the evaluators see without a key: everything still works."
5. "One bad morning, and nothing was lost, nobody chased anyone, and every deadline was watched. Now let's show you how it works."

---

## Part B: how it works (9:30)

### B1 · The restaurant (9:30–11:15) · all
*Screen: `docs/diagrams/architecture.png`. Point at each box as it's named.*

- **Dev:** "The easiest way to picture I-ITSM is a restaurant."
- **Hitesh:** "The dining room is the **frontend**: the website you just watched, built with React. It shows you things and takes your order, but it never cooks."
- **Rahul:** "The waiter is the **API**. Every click becomes a written order, called a request, carried to the kitchen, and the answer comes back on the same tray."
- **Rahul:** "The kitchen is the **backend**, written in Java with Spring Boot. The real work happens here: scoring the priority, picking the technician, watching the deadlines."
- **Dev:** "On the kitchen wall hangs the head chef's rulebook: our **domain model**, the Java classes that say what an incident is and which moves are allowed. A chef never serves a dish that isn't cooked, and our system never lets an incident jump from NEW straight to CLOSED."
- **Rahul:** "The pantry is the **database**, PostgreSQL, so nothing is lost if the kitchen restarts. The order book, where every order is written down with a time and a name, is our **audit trail**."
- **Hitesh:** "The kitchen bell is the **WebSocket**: instead of every customer asking 'is my food ready?', the kitchen rings the moment something changes. That's why the screens updated without a refresh."
- **Rahul:** "The wristband you get at the door is the **login token**, a JWT. You show it with every order, so the kitchen knows who you are and what you may order. An employee's wristband doesn't open the manager's office."
- **Rahul:** "And **Docker** packs the whole restaurant into three shipping containers, so anyone can open the same restaurant with one command."

### B2 · Follow one click (11:15–12:15) · Rahul
*Screen: the architecture diagram.*

"Let's follow Raj's report through the restaurant.

1. The website sends the form, with Raj's wristband, to the kitchen.
2. Security checks the wristband: is it real, and may Raj report incidents?
3. The kitchen's front desk, a controller, passes the order to a service, the cook.
4. The cook follows the rulebook: look for duplicates, guess the category, score the priority, set the deadlines, pick a technician.
5. A repository, the pantry staff, saves it in the database.
6. Then the kitchen calls out 'new incident!'. Three listeners hear it: the audit writes the history, the notifier sends the bell, email and SMS, and the dashboard pushes the change to every manager's screen.
7. The answer goes back to Raj's page, all in a moment."

### B3 · The website (12:15–13:30) · Hitesh
*Screen: the website, switching between the roles' tabs.*

- "There's one website, but each role sees its own version: employees report and track, technicians get a work queue, and the manager gets the dashboard, the settings and the reports."
- "The buttons aren't decided by the website. It asks the backend 'what's allowed right now?', so it never shows a button for an illegal move, like closing an incident nobody fixed."
- "While Raj typed, the page quietly asked the backend for a category guess and for similar incidents. That's how the suggestions appeared before he sent it."
- "We wanted it calm: warm colours in dark and light mode, big clear numbers, and everyday words like 'Urgent' and '12 min left'. P1 and SLA are still there, in small print, for the experts." Show dark and light mode for a second each.
- "The bell and the live dashboard keep a WebSocket line open, and each tab keeps its own login. That's how four people shared one laptop in this video."

### B4 · The kitchen: backend, security and data (13:30–15:10) · Rahul
- "The backend has four layers, like a kitchen line: controllers take the orders, services do the work, the domain holds the rules, and repositories talk to the database. Each layer only talks to its neighbour, so a change in one place doesn't break the others."
- "We never store passwords. We store a scrambled fingerprint made with BCrypt, which can't be turned back into the password."
- "Every rule is checked in the backend, not just on the screen." *Optional, 20 seconds, with Part A's data still there:* in the `emp.meera` tab, open `localhost:5173/incidents/1`, which is Raj's incident: "You can only view incidents you reported." Then, in the terminal, try to close Raj's SSO ticket straight through the API:

        T=$(curl -s -X POST localhost:8080/api/auth/login \
          -H 'Content-Type: application/json' \
          -d '{"username":"emp.raj","password":"password123"}' \
          | sed -E 's/.*"token":"([^"]+)".*/\1/')
        curl -i -X POST localhost:8080/api/incidents/3/close \
          -H "Authorization: Bearer $T"

    The answer is `409` with `INVALID_STATE_TRANSITION`: "Even without the website, nobody can close an incident that was never fixed."

- "The database keeps users, incidents, notes, the audit trail, notifications, services, categories and SLA policies in related tables." Show the ER diagram for five seconds.
- "Notifications use the **Observer** pattern: the incident only announces what happened, and listeners decide what to do. Adding WhatsApp would be one new class, without touching the incident."
- "A background timer checks every open incident every 10 seconds. That's what warned Arjun before his deadline, and what moved Priya's ticket to Arjun. In this demo the timers are fast; normally escalation waits 10 minutes."

### B5 · The brain: design and the smart features (15:10–17:05) · Dev
- "Three ideas from our OOP course do most of the work."
- "**Inheritance:** an Employee, a Technician and an Administrator are all Users. They share the login details, and each adds its own extras, like a technician's skills."
- "**The State pattern:** each status, like NEW or IN_PROGRESS, is a small object that knows its legal next moves. Anything else throws an error. That's the 409 you just saw, and it's also where the website's buttons come from."
- "**The Strategy pattern:** scoring priority and picking a technician are plug-ins: two ways to score, four ways to assign, and the manager switched to round robin live."
- "Now the smart rules, in plain words."
  - "**Priority:** impact times urgency times how important the service is, each from 1 to 4, plus a bonus when many users are hit. Raj's outage scored 4 × 3 × 4 = 48, plus 8 for 400 users, so 56. Anything from 36 up is a P1."
  - "**Category:** each category has keywords. A keyword found as a whole word scores 2 in the title and 1 in the description, and the category with the most points wins."
  - "**Technician:** every available technician with room for more work gets a score out of 110: 40 for the right skill, up to 25 for free time, up to 20 for experience, up to 15 for knowing that service, and 10 for a team lead on a P1 or P2. Arjun won the payment outage."
  - "**Duplicates:** we compare a new report with the open ones on the same service. We turn both reports into sets of key words, then divide the words they share by all the different words in both. Meera's report shared 27 %, above our 25 % line, so it was flagged and linked."
  - "**Suggested fixes:** a resolved incident's fix is offered to similar open ones, like Meera's."

### B6 · The AI helper (17:05–18:20) · Dev, Rahul, Hitesh
*Screen: the app, with the top bar in view.*

- **Dev:** "Back to our restaurant. The kitchen now has a phone line to a specialist: Claude, Anthropic's AI. The kitchen phones it to read Raj's screenshot, to chat with Meera, to understand my search and to word the updates. But the head chef still decides: our own rules set the priority and pick the technician."
- **Rahul:** "The AI only suggests and never saves anything on its own: Raj and Meera sent their reports themselves. Every answer is checked against our own lists, so it can only pick a service, a category or an urgency that really exists."
- **Hitesh:** "If the phone line is down, the kitchen follows its standard recipes. That's rules mode: forms are typed by hand, search looks for exactly what you type, and updates use fixed messages." In the `admin` tab, switch **AI assistance** back on, and the flat line starts to move (if Part A ran in rules mode, skip this and point at the reason next to the flat line). "The sign by the door tells everyone which is happening: a moving wave for AI live, a flat line for rules mode, with the reason."
- **Rahul:** "The key stays in a private file outside the project, never on GitHub, and each use costs about one US cent."

### B7 · Tested, and our answer (18:20–19:00) · Hitesh, then all
- **Hitesh:** "We wrote 111 automated tests, covering normal use, edge cases, bad input, errors, permissions, business rules and every AI fallback, and they all pass." *(Optional: show the `mvn test` result for three seconds.)*
- **Dev:** "The brief asked how object-oriented design makes an incident system easy to maintain, to extend and to make smart."
- **Rahul:** "Our answer: rules that can change live, a lifecycle that can't be broken, side effects that simply plug in, and an AI that helps but never decides."
- **Hitesh:** "Every rule is backed by a test, and the code is on GitHub at the link on screen. Thanks for watching." *Show the repository link.*

---

## What the story covers

| Required by the brief | Where you show it |
|---|---|
| Three roles, each with its own access | every scene; B3, B4 |
| Registration and login | Scene 1 |
| Create, view, update, assign, resolve and close an incident | Scenes 1, 3, 5 |
| Classification and priority | Scenes 1, 2, 4; B5 |
| SLA deadlines and warnings | Scene 1, Scene 5; B4 |
| Assignment strategies, switchable live | Scene 1, Scene 4 |
| Notifications on more than one channel | Scenes 1, 3, 5 (the bell); Scene 6 (Audit log → All notifications: in-app, email and SMS rows); B2, B4 |
| Audit history | Scenes 5, 6 |
| Search with at least five filters, reports, dashboard | Scene 6 |
| Workflow 1: report → prioritise → assign → notify | Scene 1 |
| Workflow 2: investigate → SLA watched → resolve → notify | Scenes 3, 5 |
| Workflow 3: duplicates → linked → escalated | Scenes 2, 4 |

| Smart feature | Where |
|---|---|
| Automatic category | Scenes 1, 2, 4 |
| Duplicate detection and linking | Scene 2 |
| Smart technician choice | Scene 1; B5 |
| SLA risk warning | Scene 5 |
| Automatic escalation | Scene 4 |
| Suggested fixes from history | Scene 5 |
| Live dashboard | Scene 1; B1 |
| Analytics: MTTA, MTTR, SLA compliance | Scene 6 |
| One-command Docker deployment | B1 |

| AI feature (Claude, with a rules fallback) | Where | Brief's innovation list |
|---|---|---|
| Screenshot to ticket | Scene 1; B6 | AI-assisted incident classification |
| Chat reporting | Scene 4; B6 | Chatbot-based incident reporting |
| Plain-English search | Scene 6; B6 | beyond the list |
| Plain-language updates | Scenes 1, 3, 5 | beyond the list |
| "In plain words" summary of all the figures so far | Scene 6 | Analytics and visualization |
| AI mode indicator, AI switched off live | Scenes 1, 6; B6 | beyond the list |

## Timing budget
| Part | Minutes |
|---|---|
| Part A: Scenes 0–6 | 9.5 |
| Part B: B1–B7 | 9.5 |
| **Total** | **19** |

**Clock checkpoints for Part A** (times on the recording clock; the rules come from the demo SLA and the 2-minute escalation):

| Moment | Aim for | The rule behind it |
|---|---|---|
| Raj sends INC-000001 | about 1:45 | every later time counts from here |
| Arjun acknowledges it | about 2:25 | within 2 minutes of Raj's Send report |
| Raj sends the SSO report, INC-000003 | about 3:40 | escalates if nobody touches it for 2 minutes |
| Dev shows the escalation (bell, INC-000003) | about 6:40 | at least 2 min 10 s after the SSO report was sent |
| Scene 5 starts, SLA warning in Arjun's bell | about 6:55 | the warning lands about 5 minutes after Raj's Send report |
| Arjun resolves INC-000001 | about 7:20 | before 9 minutes after Raj's Send report, so the SLA is met |

## If something goes wrong
- "No technician available": that technician is at capacity, so raise their maximum (the number after the slash under *Workload*) in *People*.
- A duplicate warning blocks a report you wanted: click *It's a different problem — submit anyway*.
- No duplicate warning for Meera: INC-000001's title or description isn't exactly the Scene 1 text. Restart, and check both before Send report next time.
- The dashboard's live marker stays on "Connecting…": reload the tab once.
- The escalation needs 2 full minutes with no change to INC-000003. Fill the wait with Scene 4's admin steps and Meera's chat.
- The SLA warning isn't in Arjun's bell yet: it lands about 5 minutes after Raj's Send report. Open INC-000001 and talk through its time-left meter until it arrives.
- The AI takes more than about 10 seconds: fill in the fields by hand and carry on. The clocks don't wait. One slow or failed answer only affects that request. Two in a row put every AI feature in rules mode for 60 seconds (the indicator says *AI not responding*), and the bell updates in that minute stay templates tagged Rules; carry on, or switch **AI assistance** off and on in *Settings* to end the wait at once.
- The bell's newest update still says Rules: the AI wording arrives a few seconds after the change. Wait a moment or reopen the bell; after about 10 seconds, read the template and carry on.
- The chat's summary picks the wrong service or category: click **Edit**, choose Desk Phones (and *Let the system decide* for the category) in the form and press **Send report**. If the chat itself fails, close it (what Meera typed goes into the form) or fill in the form: title "Desk phone cannot dial", What happened "My desk phone cannot dial out since this morning", service Desk Phones, impact Low, urgency Medium, 1 person affected.
- The top bar says **Rules mode**: read the reason next to it. *No key*: check the `ANTHROPIC_API_KEY=` line in `~/.config/i-itsm/ai.env`, then run `docker compose --env-file ~/.config/i-itsm/ai.env up -d` again (it restarts the backend with the key, so log in again in every tab). *Key not accepted*: put a working key in that file and run the same command. *AI not responding*: two calls in a row failed; the app tries again after 60 seconds, or at once if you switch **AI assistance** off and on. *Switched off*: turn **AI assistance** back on in *Settings*. *Today's limit reached* or *credits ran out*: record anyway in rules mode and follow the *Rules mode* notes in each scene.
- Too slow and the P1 missed its deadline? Run `docker compose down -v && docker compose --env-file ~/.config/i-itsm/ai.env up -d`, log in again in all four tabs, set the demo SLA for P1 again (`-v` resets it; Before recording, step 6), check the indicator says AI live, and restart Part A. To keep logins across restarts, add `JWT_SECRET=` with 32+ characters to the same `~/.config/i-itsm/ai.env` file, or set it in your shell.
- Don't over-claim: email and SMS are simulated, "linked" means similar words on the same service, and the AI only suggests: the employee checks the form and presses Send report.
