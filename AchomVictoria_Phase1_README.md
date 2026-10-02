# LearningHub: schedule-service and payment-service

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

---

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

## 5. Who is calling? (Phase 1)

Authentication (Keycloak JWT) arrives in Phase 2. Until then, the caller comes from two headers:

| Header | Example | Default when missing |
|---|---|---|
| `X-User` | `sstudent`, `janderson`, `admin` | `sstudent` |
| `X-Role` | `student`, `teacher`, `admin` | `student` |

Only `CallerResolver.java` reads these headers. In Phase 2 it will read the token instead, and nothing else changes.

Seed users:
- Students: `sstudent`, `jsmith`, and `mgarcia` (has an overdue invoice). Any other student username, e.g. `alee`, has no invoice yet.
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

Import `AchomVictoria_Phase1_Postman.json` into Postman. It has 62 requests with assertions, in 7 folders:

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

## 9. Reflection and next steps

**Decisions**

- **One owner per entity.** I first stored course names and teachers on `class_session`, as an early version of the team spec did. When the team finalised the canonical model, I moved them out: schedule-service now owns only the slot and asks enrollment-service for course details. This removed duplicated data that could drift out of sync.
- **Term invoices with lines.** Invoices are keyed by `(username, term_id)` with an `invoice_line` per course. The breakdown is stored history: it shows what was billed, even if the student later drops a course.
- **Computed views.** Timetables are computed from weekly slots, and invoice status is computed from dates. Nothing goes stale, and no background job is needed.
- **Flyway over `ddl-auto`.** I started with Hibernate generating the schema. I switched to versioned migrations so a fresh production database builds itself, and Hibernate only validates.
- **Graceful degradation.** Every cross-service call has a timeout and a fallback, so each service can be developed and demonstrated on its own.

**Challenges**

- The skeleton's `ddl-auto: update` with `data.sql` would re-insert the seed rows on every restart, colliding with unique keys. Separating schema and seed data into Flyway locations, chosen per profile, fixed this.
- I needed a realistic neighbour without the full team stack, so I wrote a small contract stub of enrollment-service in the team API shape.

**Phase 2 plan**

- Replace `CallerResolver`'s headers with Keycloak JWT validation (realm roles → `ROLE_*`), and forward the bearer token on calls to enrollment-service and schedule-service.
- Register with Eureka, route through the team gateway, and call neighbours by `lb://` name.
- Swap the enrollment stub for Renae's service.
- Write a Gatling stress test on `/api/schedule/week` and `/api/payments/summary`.
