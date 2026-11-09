# LearningHub: schedule-service and payment-service

> **Phase 2 (Security) is in [section 0](#0-phase-2-security-gateway-discovery-stress-test).** Every request now goes through the gateway at `http://localhost:8090` and needs a Keycloak bearer token. Phase 1 (canonical data model) is tagged [`phase1`](../../tree/phase1).

**SFWE 410/510 Course Project, Phase 1 (Canonical Data Model)**
Victoria Achom · part of the LearningHub team project · built on the team skeleton [David-Coulter/lms-skeleton](https://github.com/David-Coulter/lms-skeleton)

LearningHub is a school portal for students, teachers and administrators, built as microservices with one database per bounded context. This repository contains my two services, **schedule-service** (Scheduling context) and **payment-service** (Payments context), plus the **config-service** that serves their `dev` and `prod` profiles.

| Container | Port | Database | Base path | Role |
|---|---|---|---|---|
| config-service | 8889 (host) | none | `/{service}/{profile}` | Spring Cloud Config Server: dev and prod profiles |
| schedule-service | 8103 | `scheduledb` | `/api/schedule` | Terms (quarters) and the timetable slot of each course |
| payment-service | 8107 | `paymentdb` | `/api/payments` | Term tuition invoices and lines, saved cards, billing profiles |
| enrollment-service *(dev stub)* | 8104 | none | `/api/enrollment` | Fixed JSON standing in for the team's enrollment-service |
| postgres | 5432 | one DB per service | | PostgreSQL 16 |
| **gateway-service** | **8090** (host) | none | `/api/**`, `/auth/**` | Phase 2: single entry point (Spring Cloud Gateway) |
| **discovery-service** | 8761 | none | `/eureka` | Phase 2: service registry (Netflix Eureka) |
| **keycloak** | via gateway | `keycloak` | `/auth` | Phase 2: identity provider (Keycloak 26, realm `learninghub`) |
| **mailpit** | 8025 | none | | Phase 2: catches password-reset emails |

---

## 0. Phase 2: security, gateway, discovery, stress test

### Architecture

```
                         ┌────────────── Docker network lh-internal ──────────────────────────┐
 Browser / Postman /     │                                                                    │
 Gatling ──► :8090 ──────┤► gateway-service ─┬─ /auth/**          ──► keycloak (realm learninghub)
                         │  (Spring Cloud    ├─ /api/schedule/**  ──► lb://schedule-service ×1..n
                         │   Gateway)        ├─ /api/payments/**  ──► lb://payment-service
                         │        │          └─ /api/enrollment/** ──► enrollment stub
                         │        ▼ looks up instances                                         │
                         │  discovery-service (Eureka) ◄── register ── schedule, payment, gateway
                         │                                                                    │
                         │  payment-service ──(Eureka: http://schedule-service, same bearer token)──► schedule-service
                         └────────────────────────────────────────────────────────────────────┘
```

### Run it

```bash
cp .env.example .env
docker compose down -v          # fresh database and a fresh Keycloak realm
docker compose up --build
docker compose ps               # wait until keycloak is "healthy" and gateway-service is "Up"
```

| Open | URL |
|---|---|
| API (through the gateway) | `http://localhost:8090/api/schedule/...`, `http://localhost:8090/api/payments/...` |
| Gateway routes | `http://localhost:8090/actuator/gateway/routes` |
| Eureka dashboard | `http://localhost:8761` |
| Sign in / Register / Forgot password | [`http://localhost:8090/auth/realms/learninghub/protocol/openid-connect/auth?client_id=learninghub-web&response_type=code&scope=openid&redirect_uri=http://localhost:8090/welcome`](http://localhost:8090/auth/realms/learninghub/protocol/openid-connect/auth?client_id=learninghub-web&response_type=code&scope=openid&redirect_uri=http://localhost:8090/welcome) |
| Mailpit (reset emails) | `http://localhost:8025` |
| Keycloak admin console | `http://localhost:8090/auth/admin` (user `admin`, password `admin`) |

### Test users

All passwords are **`password`**. The realm is imported from `keycloak/learninghub-realm.json` on Keycloak's first start.

| Username | Role | Notes |
|---|---|---|
| `student1` | student | Sam Student: seed invoices, cards, billing profile |
| `jsmith`, `mgarcia` | student | `mgarcia` has an overdue invoice |
| `alee` | student | No invoice yet: opening the summary raises this term's invoice |
| `janderson`, `ppatel` | teacher | See only the classes they teach |
| `admin1` | admin | School-wide views; admin writes |

### (1) Authentication

Keycloak issues signed JWT access tokens. Both services are OAuth2 **resource servers**: every `/api` request must carry `Authorization: Bearer <token>`. The token's signature, expiry and issuer are checked against Keycloak's public keys (JWKS). Requests without a valid token get **401**.

```bash
# log in (password grant, through the gateway)
TOKEN=$(curl -s localhost:8090/auth/realms/learninghub/protocol/openid-connect/token \
  -d grant_type=password -d client_id=learninghub-web -d username=student1 -d password=password \
  | sed 's/.*"access_token":"\([^"]*\)".*/\1/')

curl -i localhost:8090/api/payments/summary                                 # 401: no token
curl localhost:8090/api/payments/summary -H "Authorization: Bearer $TOKEN"  # 200
```

The caller's identity comes **only** from the token: `preferred_username` and the realm roles in `realm_access.roles`. The Phase 1 `X-User` / `X-Role` headers are ignored. The code is in `SecurityConfig.java` and `CallerResolver.java` in each service.

### (2) Authorization

Keycloak realm roles become Spring roles (`ROLE_admin`, `ROLE_teacher`, `ROLE_student`). Access is checked in two layers:

| Layer | Rule | Result if broken |
|---|---|---|
| `SecurityConfig` (before any controller) | schedule: `POST/PUT/DELETE /sessions/**`, `/terms/**` need **admin** | 403 |
| `SecurityConfig` | payment: `POST /pay` needs **student**; `POST/PUT/DELETE /invoices/**` need **admin** | 403 |
| Service layer (ownership) | a student sees only their own invoices, cards and billing; teachers see only the classes they teach | 403 / filtered |

### (3) User registration

Self-registration is switched on in the realm (`registrationAllowed`). Open the sign-in link, click **Register**, and fill in the form. Keycloak creates the account and gives it the realm's default role, which includes **student**. You are then redirected back through the gateway to `/welcome`, and the new user can log in and call the API straight away.

### (4) Password reminder

`resetPasswordAllowed` is on, and the realm's SMTP server is **Mailpit**. On the sign-in page, click **Forgot Password?** and enter a username or email (e.g. `student1`). Open `http://localhost:8025` to read the reset email, and follow its link to set a new password.

### Bearer token passed across services

When payment-service builds a student's summary, it asks schedule-service for the current term (`GET /api/schedule/terms`). schedule-service also requires a token, so payment-service **forwards the same bearer token it received** (`BearerTokenForwarder.java`, a `RestClient` interceptor). schedule-service validates it independently and logs whose token arrived:

```
GET /api/schedule/terms called with the bearer token of alee (student)
```

```bash
docker compose logs schedule-service | grep "bearer token of"
```

Postman folder 8 shows that schedule-service rejects a call without a token (401), so the payment → schedule call can only succeed with the forwarded token. Folder 2 shows it succeeding: a term invoice is raised for `alee`.

### Service discovery

- Each service registers in **Eureka** under its `spring.application.name` and renews its lease every 5 s. A stopped instance deregisters on shutdown; a crashed one is dropped after 15 s.
- The **gateway** routes `lb://schedule-service` and `lb://payment-service`. It asks Eureka for live instances and load-balances across them.
- **payment-service** calls `http://schedule-service` (a name, not a host:port) through a `@LoadBalanced` `RestClient` (`HttpClientConfig.java`). Nothing in the code hard-codes where schedule-service runs.

### Scalability and resilience: Gatling stress test

`gatling/` contains a Gatling (Java DSL) simulation that runs **inside Docker**, so no local Java or Maven is needed. Each virtual user is a student who logs in to Keycloak through the gateway, then for 60 s repeatedly calls `/api/schedule/week`, `/api/schedule/today` and `/api/payments/summary` with their token. Each summary call also triggers payment → schedule with the forwarded token. The run **fails** unless at least 95% of requests succeed and the 95th-percentile response time is under 2 s.

```bash
docker compose --profile stress run --rm gatling                          # 50 users
USERS=150 DURATION_SECONDS=90 docker compose --profile stress run --rm gatling
```

The HTML report is written to `gatling/results/<simulation>-<timestamp>/index.html`; open it in a browser.

**Scaling out.** Start a second schedule-service instance. It registers under the same name, and the gateway load-balances between both:

```bash
docker compose --profile scale up -d schedule-service-2
# Eureka (http://localhost:8761) now shows SCHEDULE-SERVICE with 2 instances
```

**Resilience.** While a stress test is running, stop one instance:

```bash
docker compose stop schedule-service      # instance 2 keeps serving
```

- The gateway uses connect and response timeouts, and **retries failed GETs** (`Retry` filter, up to 2 times with backoff). Each retry asks the load balancer again, so it lands on the surviving instance.
- Eureka removes the stopped instance.
- Every cross-service call has a timeout and a fallback, so payment-service degrades instead of failing.

The report should stay above the 95% success threshold. Bring the instance back with `docker compose start schedule-service`.

## 1. Run it

**Prerequisites:** Docker Desktop (running) and Git. Java and Maven build inside the containers. On Windows, use Git Bash.

```bash
git clone https://github.com/victoriaachom/learninghub-schedule-payment.git
cd learninghub-schedule-payment
cp .env.example .env
docker compose up --build
```

The first build takes a few minutes. The stack is ready when you see both `Started ScheduleServiceApplication` and `Started PaymentServiceApplication`. Compose starts postgres and config-service first and waits until both are healthy.

Check it from a second terminal:

```bash
curl localhost:8103/actuator/health        # {"status":"UP"}
curl localhost:8107/actuator/health        # {"status":"UP"}
curl localhost:8103/api/schedule/whoami    # DEV (from config-service) ...
curl localhost:8107/api/payments/whoami
```

| Action | Command |
|---|---|
| Stop | `Ctrl+C`, then `docker compose down` |
| Stop and **reset the database** (fresh seed data) | `docker compose down -v` |

## 2. Profiles (dev / prod) through config-service

Each service imports `configserver:http://config-service:8888` at startup. config-service serves `<service>-<profile>.yml` from `config-service/src/main/resources/config/`, and those values override the service's own `application.yml`. The active profile is `PROFILE` in `.env`.

| | dev | prod |
|---|---|---|
| Flyway locations | `db/migration` + `db/seed` | `db/migration` only |
| Data | schema + demo seed data | schema only, no demo data |
| SQL logging | on | off |
| Log level | `edu.lms: DEBUG` | `root: WARN` |
| Hibernate | `ddl-auto: validate` (both) | `ddl-auto: validate` (both) |

```bash
PROFILE=prod docker compose up --build     # switch to prod
curl localhost:8889/payment-service/dev     # what config-service serves
curl localhost:8889/payment-service/prod
curl localhost:8107/actuator/env            # shows the "configserver:" property source
```

If config-service cannot be reached, `whoami` says `NO PROFILE CONFIG`, which makes it obvious where the settings come from.

## 3. Database: Flyway migrations

Flyway owns the schema. Hibernate only validates that the entities match it (`ddl-auto: validate`).

| Service | Schema (all profiles) | Seed data (dev only) |
|---|---|---|
| schedule-service | `src/main/resources/db/migration/V1__schedule_schema.sql` | `src/main/resources/db/seed/V2__schedule_seed.sql` |
| payment-service | `src/main/resources/db/migration/V1__payment_schema.sql` | `src/main/resources/db/seed/V2__payment_seed.sql` |

- Seed dates are relative to the day the database is created, so the current term is always in progress. Run `docker compose down -v` to recreate it.
- Applied migrations: `curl localhost:8103/actuator/flyway`.
- Inspect tables: `docker compose exec -T postgres psql -U lms -d paymentdb -c '\dt'`.

## 4. Canonical data model and bounded contexts

The team's canonical model is split into independently owned contexts, with one owner per entity:

| Bounded context | Service | Owns | Database |
|---|---|---|---|
| **Scheduling** | **schedule-service** | **Term, ClassSession (slot)** | **scheduledb** |
| **Payments** | **payment-service** | **BillingProfile, PaymentMethod, Invoice, InvoiceLine** | **paymentdb** |
| Catalog & Enrollment | enrollment-service | GradeLevel, Course, Enrollment | enrollmentdb |
| Identity & Profiles | user-service | Account (Keycloak), Profile | userdb |
| Learning / Assessment / Analytics | learning, assessment, dashboard | (other team members) | |

Across databases, rows refer to each other only by **shared ids**, never foreign keys:
- `username`, the Keycloak account
- `term_id`, owned by schedule-service
- `course_id`, owned by enrollment-service

**scheduledb** (2 tables)

| Table | Columns | Notes |
|---|---|---|
| `term` | `id` PK text, `name`, `start_date`, `end_date`, `is_current`, `max_credits`, `quarter`, `academic_year` | Every service reads terms from `GET /api/schedule/terms` |
| `class_session` | `id` PK, `course_id` **unique**, `term_id` FK → term (cascade), `days` jsonb, `start_time`, `end_time`, `start_date`, `end_date` | Only the slot. Course name, grade level, teacher and colour belong to enrollment-service and are looked up at request time |

**paymentdb** (4 tables)

| Table | Columns | Notes |
|---|---|---|
| `billing_profile` | `id` PK, `username` unique, `name`, `email`, `address` | |
| `payment_method` | `id` PK, `username`, `brand`, `last4`, `expires`, `is_default` | Never the full card number |
| `invoice` | `id` PK, `username`, `term_id`, `description`, `amount` numeric(10,2), `due_date`, `status`, `paid_on`, `method_id` FK → payment_method (set null) | **Unique (username, term_id)**: one tuition invoice per student per term |
| `invoice_line` | `id` PK, `invoice_id` FK → invoice (cascade), `course_id`, `description`, `amount` | One line per course, $320 each |

Each service has the same layers: **controller → service → repository → entity → database**.

## 5. Who is calling? (Phase 1: replaced in Phase 2 by Keycloak tokens, see section 0)

Authentication (Keycloak JWT) arrives in Phase 2. Until then, the caller comes from two headers:

| Header | Example | Default when missing |
|---|---|---|
| `X-User` | `student1`, `janderson`, `admin` | `student1` |
| `X-Role` | `student`, `teacher`, `admin` | `student` |

Only `CallerResolver.java` reads these headers. In Phase 2 it will read the token instead, and nothing else changes.

Seed users:
- Students: `student1`, `jsmith`, and `mgarcia` (has an overdue invoice). Any other student username, e.g. `alee`, has no invoice yet.
- Teachers: `ppatel`, `janderson` and others.
- Admin: `admin`.

## 6. Endpoints

### schedule-service (`/api/schedule`): 8 team endpoints, plus CRUD

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/today?date=` | all | Class meetings on a date (default today) |
| GET | `/week?date=` | all (own) | Mon–Sun timetable: hours, blocks, mini calendar, next 4 meetings |
| GET | `/month?year=&month=` | all | Month grid (`firstDow` 0 = Sunday) |
| GET | `/term` | all | One bar per course across the current term (% positions) |
| GET | `/terms` | all | Terms; every other service reads them here |
| GET | `/sessions` | all | Every slot, unfiltered: reference data enrollment uses to add times to its catalogue |
| PUT | `/sessions/by-course/{courseId}` | admin | Upsert a course's slot (sent by enrollment when a subject is saved). `endTime` must be start + 1 h; `termId` defaults to the current term |
| DELETE | `/sessions/by-course/{courseId}` | admin | 204 removed, 404 none |
| GET/POST, GET/PUT/DELETE | `/sessions`, `/sessions/{id}` | writes admin | Slot CRUD (409 if a course already has a slot) |
| POST, GET/PUT/DELETE | `/terms`, `/terms/{id}` | writes admin | Term CRUD (one current term; delete cascades to slots) |

**Role filtering:** students see the courses they're enrolled in, teachers the courses they teach (from enrollment's course catalogue), and admins everything.

### payment-service (`/api/payments`): 7 team endpoints, plus CRUD

| Method | Path | Who | What |
|---|---|---|---|
| GET | `/summary` | all | Four stat cards. A student with no invoice for the current term **gets one raised** from their enrolments (one $320 line per course). Admin sees school-wide totals (`scope: school`) |
| GET | `/upcoming` | all | Unpaid invoices by due date; the first row gets `Pay Now`. Admin sees all, a teacher sees none |
| GET | `/history` | all | Paid invoices, newest first |
| GET | `/methods` | all | Caller's cards |
| GET | `/billing` | all | Caller's billing profile |
| GET | `/invoices/{id}/details` | owner/admin | Lines, status, receipt, timeline (403 / 404) |
| POST | `/pay` | student | Pay by `id`, else `description`, else the next unpaid invoice, with the given or default card (409 if already paid) |
| GET/POST, GET/PUT/DELETE | `/invoices`, `/invoices/{id}` | writes admin | Invoice CRUD with lines (409 on a duplicate student + term) |
| POST/PUT/DELETE | `/methods`, `/methods/{id}` | owner | Card CRUD; a new default clears the old one |
| PUT/DELETE | `/billing` | owner | Upsert or delete the billing profile |

**Displayed status** is computed, never stored: **Paid**; **Overdue** (unpaid and past due); **Upcoming** (earliest open invoice); **Scheduled** (later ones).

### Inter-service communication

| From | Calls | Why | If unavailable |
|---|---|---|---|
| schedule-service | enrollment `GET /api/enrollment/courses` | Course names, grade levels, teachers, colours | Shows course ids; teacher filter off |
| schedule-service | enrollment `GET /api/enrollment/state` | A student's enrolled course ids | Shows all slots |
| payment-service | schedule `GET /api/schedule/terms` | The current term, to raise its invoice | No invoice raised |
| payment-service | enrollment `GET /api/enrollment/my-classes` | The student's courses, one invoice line each | No invoice raised |
| payment-service | enrollment `GET /api/enrollment/courses` | Grade level and credits on invoice lines | Lines without them |

All calls use a 1 s connect / 2 s read timeout, so each service degrades instead of failing.

**The enrollment stub.** In this repository, `enrollment-service` is a dev-only nginx stub (`stubs/enrollment/`) that returns fixed JSON in the team API shape. To use the real service, replace that block in `docker-compose.yml`.

## 7. Testing with Postman

**Phase 2:** import `AchomVictoria_Phase2_Postman.json` (82 requests, 9 folders). The collection logs each test user in to Keycloak through the gateway and sends a real bearer token. A request names its user in an `X-User` header, which the script swaps for `Authorization: Bearer <token>`. The first request clears cached logins, because Keycloak gets new signing keys whenever the database is wiped. Folder 7 covers the gateway and Eureka; folder 8 covers authentication, authorization and the forwarded token. Check that the collection variables are `gateway` = `http://localhost:8090` and `config` = `http://localhost:8889`.

*Phase 1 collection:* `AchomVictoria_Phase1_Postman.json` has 62 requests with assertions, in 7 folders (it predates authentication, so it runs against the `phase1` tag):

0. Health, config server, profiles and neighbours
1. Payment views
2. Raising a term invoice from enrolments
3. Payment admin view and errors
4. Payment CRUD
5. Schedule views
6. Schedule slot sync and CRUD

```bash
docker compose down -v && docker compose up --build    # fresh dev data first
npm install -g newman                                  # optional: run headless
newman run AchomVictoria_Phase1_Postman.json
```

To test prod, set the collection variable `profile` to `prod` and run folder 0.

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `curl: (52) Empty reply` right after start | Still booting. Wait for `Started ...Application` |
| `Found non-empty schema(s) without schema history table` | The database was created before Flyway. Run `docker compose down -v` |
| `port is already allocated` (5432) | A local PostgreSQL is running. Stop it |
| Postman "Pay" test fails on a second run | The seed invoices are already paid. Run `docker compose down -v` |
| Windows: `the input device is not a TTY` | Add `-T` to `docker compose exec` |
| Every request returns 401, even `/actuator/health` | A stale token from before `down -v` (Keycloak has new keys). Run the collection from the start; its first request clears cached logins |
| Gateway not starting | It waits for Keycloak to be `healthy` (about 1 minute). Check with `docker compose ps` |
| Keycloak `Unable to find composite realm role` or import errors | Fix `keycloak/learninghub-realm.json`, then `docker compose down -v` (the realm is only imported when it doesn't exist yet) |
| `port is already allocated` on 8090, 8761 or 8025 | Another program uses it; change the host side of that port in `docker-compose.yml` |

