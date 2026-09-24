# EduSupport

EduSupport is a student support and institutional ticket management system for fees, attendance, identity cards, documents, certificates, and other administrative requests.

## Problem statement

Students need a clear way to raise and follow administrative requests. Staff need accountable ownership, controlled workflow, SLA visibility, pending-action handling, and an auditable history. Managers need institution-wide operational visibility without weakening student or staff data boundaries.

## Key features

- JWT login with `STUDENT`, `STAFF`, and `ADMIN` roles.
- Ticket creation, controlled lifecycle transitions, priorities, assignment, and reassignment.
- Student-owned and staff-assigned access boundaries enforced in backend services.
- BCrypt password hashing and profile-gated development seed data.
- Comments, activity history, status history, assignment history, escalation history, and pending-action history.
- Backend-owned SLA ageing, approaching-SLA, overdue, and escalation state.
- Student, staff, and admin dashboard APIs with server-side management filters.
- Responsive Bootstrap 5 and vanilla JavaScript frontend with loading, empty, error, toast, and confirmation states.

## Technology stack

- Java 17 target
- Spring Boot 3.4.x
- Spring Web, Spring Data JPA, Spring Security 6
- JWT with JJWT
- Jakarta Bean Validation
- MySQL 8
- HTML5, Bootstrap 5, Bootstrap Icons, Chart.js, vanilla JavaScript

## Architecture overview

```text
Vanilla frontend
	|
	v
REST API / JSON DTOs
	|
	v
Controllers -> Services -> Repositories -> MySQL 8
	|
	+-> Spring Security JWT/RBAC
	+-> SlaService and activity history
```

The backend is the source of truth for authorization, workflow, SLA calculations, dashboard metrics, and escalation state. See [ARCHITECTURE.md](ARCHITECTURE.md) for the detailed design.

## Project structure

```text
src/main/java/com/edusupport/
  auth/                 Login service and controller
  category/             Ticket category entity and repository
  common/               Health, dashboard, and API error handling
  security/             JWT filter, principal, and Spring Security config
  seed/                 Demo-profile startup seeder
  ticket/               Ticket, comments, history, SLA, assignment, and workflow
  user/                 User entity, roles, and repository
src/main/resources/     Datasource and security configuration
src/test/java/          Unit, authorization, workflow, SLA, and dashboard tests
frontend/index.html     Login and application shell
frontend/app.js         API client, routing, views, and interactions
frontend/css/style.css  EduSupport visual system
```

## Prerequisites

- JDK 17 or a compatible newer JDK configured to compile with release 17.
- Maven 3.9 or newer.
- MySQL 8 with a database named `edusupport`.
- A browser with JavaScript enabled.

## MySQL setup

Create the database and a least-privilege application user using your local MySQL administration process. Do not commit credentials. The application uses Hibernate `ddl-auto: update` for this assessment; production deployments should use versioned migrations instead.

## Environment variables

Required:

```text
DB_USERNAME=<local MySQL username>
DB_PASSWORD=<local MySQL password>
JWT_SECRET=<local JWT signing secret, at least 32 bytes>
```

Optional:

```text
DB_URL=jdbc:mysql://localhost:3306/edusupport
PORT=8080
JWT_EXPIRATION_MS=3600000
FRONTEND_ALLOWED_ORIGINS=http://localhost:4173
```

Set these in the process that starts Spring Boot. Never place real values in source control.

## Run the backend

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080` by default. Health is available at `GET /api/health`.

## Run the frontend

The frontend is static and can be served from the `frontend/` directory. For a local PowerShell preview:

```powershell
python -m http.server 4173 --directory frontend
```

Open `http://localhost:4173`. The frontend uses `http://localhost:8080/api` when served on port 4173 and otherwise falls back to same-origin `/api`. Set `window.EDUSUPPORT_API_ROOT` before loading the app when a different API origin is required.

## Demo profile

Demo data is isolated behind the `demo` Spring profile and is never enabled by default:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

The idempotent seed creates these accounts only when their email does not already exist:

| Role    | Email                    |
| ------- | ------------------------ |
| STUDENT | `student@edusupport.com` |
| STAFF   | `staff@edusupport.com`   |
| ADMIN   | `admin@edusupport.com`   |

The development password is intentionally not documented here. Obtain it from the local development setup without committing or sharing it. Passwords are BCrypt-hashed before persistence, and existing users are not overwritten.

## API overview

Authentication:

- `POST /api/auth/login`

Tickets and workflow:

- `POST /api/tickets`
- `GET /api/tickets/{ticketId}`
- `GET /api/tickets/mine`
- `GET /api/tickets/assigned-to-me`
- `GET /api/tickets`
- `PUT /api/tickets/{ticketId}/assignment`
- `PUT /api/tickets/{ticketId}/status`
- `POST /api/tickets/{ticketId}/student-response`
- `POST` and `GET /api/tickets/{ticketId}/comments`
- `GET /api/tickets/{ticketId}/history`
- `GET /api/tickets/{ticketId}/sla`
- `GET /api/tickets/overdue`
- `GET /api/tickets/approaching-sla`
- `GET /api/tickets/pending-student`
- `GET /api/tickets/escalated`
- `POST` and `DELETE /api/tickets/{ticketId}/escalation`

Dashboards:

- `GET /api/dashboard/student`
- `GET /api/dashboard/staff`
- `GET /api/dashboard/admin`

## User roles

- `STUDENT`: create tickets, view own tickets, comment, respond to pending requests, and see own SLA/action state.
- `STAFF`: view and process assigned tickets, comment, request student information, and resolve assigned work.
- `ADMIN`: view organization-wide tickets, assign/reassign, monitor SLA and workload, escalate/de-escalate, and use management dashboards.

## Ticket lifecycle

```text
NEW -> TRIAGED -> ASSIGNED -> IN_PROGRESS
IN_PROGRESS -> PENDING_STUDENT -> IN_PROGRESS
IN_PROGRESS -> RESOLVED -> CLOSED
RESOLVED -> REOPENED -> IN_PROGRESS
```

The backend rejects arbitrary transitions. Assignment does not happen implicitly during status changes.

## SLA policy

| Priority |   Target |
| -------- | -------: |
| CRITICAL |  8 hours |
| HIGH     | 24 hours |
| MEDIUM   | 48 hours |
| LOW      | 72 hours |

SLA age, remaining time, approaching state, and overdue state are calculated by the backend `SlaService` using UTC. Approaching SLA means the final 20% of the SLA window. Resolved, closed, and cancelled tickets are not actively overdue. SLA continues ageing while `PENDING_STUDENT`; the due time is not paused or reset.

## Pending-student workflow

Staff or administrators must supply a reason when moving a ticket to `PENDING_STUDENT`. That reason becomes a ticket comment and activity history. Only the owning student can call `POST /api/tickets/{ticketId}/student-response`; the response becomes a comment and moves that ticket back to `IN_PROGRESS`.

## Escalation workflow

Only administrators can manually escalate or de-escalate tickets. Escalation stores a reason, timestamp, actor, and history action. Overdue tickets are visible as escalation candidates but are not automatically escalated.

## Testing

```powershell
mvn test
Get-ChildItem frontend -Filter *.js -Recurse | ForEach-Object { node --check $_.FullName }
```

## Known limitations

- Final review did not run an authenticated Playwright credential flow.
- Final review did not run a real-MySQL integration test.
- Resolution stores status and timestamps but does not yet include a resolution summary/code field.
- Ticket creation does not submit a category because the current backend create DTO does not support it; categorization is currently a triage concern.
- Dashboard aggregation is in-memory and intended for assessment-scale data.
- Browser sessions keep JWTs in memory, so a page refresh requires signing in again.
