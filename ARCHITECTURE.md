# EduSupport Architecture

## System shape

```text
Frontend: HTML5 + Bootstrap 5 + Bootstrap Icons + Chart.js + vanilla JavaScript
                              |
                              v
                       REST API / JSON DTOs
                              |
                              v
                       Spring Controllers
                              |
                              v
                         Service layer
                              |
                              v
                    Spring Data JPA repositories
                              |
                              v
                            MySQL 8
```

The frontend is a separate static application. The backend remains the source of truth for authentication, authorization, ticket workflow, SLA assessment, pending actions, escalation, and dashboard metrics.

## Backend layers

### Controllers

Controllers own HTTP concerns, request validation, authentication principal extraction, status codes, and DTO mapping. They do not expose JPA entities directly.

Main surfaces:

- `AuthController`: login.
- `TicketController`: ticket creation, reads, status changes, assignment, comments, history, SLA, pending response, escalation, and filtered operational lists.
- `DashboardController`: student, staff, and admin dashboards.
- `HealthController`: health probe.

### Services

Services own business rules and transactional changes:

- `AuthService`: delegates credential verification and creates JWT responses.
- `TicketService`: owns ticket creation, access checks, status transitions, assignment, comments, pending responses, escalation, history, and role-scoped ticket queries.
- `SlaService`: owns UTC-based age, remaining time, approaching-SLA, and overdue calculations.
- `DashboardService`: consumes role-scoped ticket service methods and `SlaService`; it does not recreate SLA windows.
- `DemoDataSeeder`: profile-gated `CommandLineRunner` for idempotent local data.

### Repositories

Spring Data repositories persist users, categories, tickets, ticket comments, and the shared `ticket_history` activity table. Repository methods provide ownership and assignment scopes such as student-owned and staff-assigned ticket lists.

## Authentication and RBAC

1. `POST /api/auth/login` authenticates email and password through a `DaoAuthenticationProvider`.
2. Passwords are checked against BCrypt hashes.
3. `JwtService` signs a short-lived JWT containing user ID, name, email subject, and role.
4. `JwtAuthenticationFilter` validates the bearer token on protected requests.
5. `UserPrincipal` exposes role authorities such as `ROLE_STUDENT`, `ROLE_STAFF`, and `ROLE_ADMIN`.
6. Spring Security route rules and service-layer checks both enforce authorization.

The frontend hides role-inappropriate navigation for usability, but backend authorization remains authoritative.

## DTO boundary

Ticket, comment, history, SLA, and dashboard response records expose only fields required by the client. Password hashes, JWT values, and internal security fields are not returned. Dashboard DTOs contain backend-derived SLA states rather than requiring frontend calculations.

## Ticket lifecycle

```text
NEW -> TRIAGED -> ASSIGNED -> IN_PROGRESS
IN_PROGRESS -> PENDING_STUDENT -> IN_PROGRESS
IN_PROGRESS -> RESOLVED -> CLOSED
RESOLVED -> REOPENED -> IN_PROGRESS
```

`TicketTransitionPolicy` rejects unsupported transitions. `PENDING_STUDENT` is explicit: a staff/admin reason becomes a comment, and only the owning student can respond through the controlled student-response operation.

## Ownership and assignment

- Students are authorized against `ticket.student`.
- Staff are authorized against `ticket.assignedStaff`.
- Administrators have organization-wide access.
- Only administrators can assign or reassign.
- Assignees must exist and have the `STAFF` role.
- Assignment and reassignment are stored in the existing `TicketHistory` table.
- Status changes do not silently change ownership.

## Comments and activity history

`TicketComment` stores ticket, author, content, and creation time. Comments are returned in ascending creation order.

`TicketHistory` is the single activity model. Its action enum covers creation, assignment, reassignment, status changes, comments, escalation, de-escalation, and reserved priority-change activity. Generic before/after values explain status and escalation changes while staff relationship fields explain assignment changes.

## SLA service

`SlaPolicy` defines the single priority policy:

- `CRITICAL`: 8 hours
- `HIGH`: 24 hours
- `MEDIUM`: 48 hours
- `LOW`: 72 hours

`SlaService` receives an injectable UTC `Clock`, making boundary tests deterministic. It calculates age, remaining seconds, approaching state, and overdue state. Approaching means the final 20% of the window. `RESOLVED`, `CLOSED`, and `CANCELLED` tickets are inactive for overdue purposes. SLA continues while `PENDING_STUDENT` and becomes active again for `REOPENED` tickets.

## Escalation

Escalation state is stored on `Ticket` with reason, actor, timestamp, and de-escalation fields. Only administrators can change it. Each change writes an activity-history record. Overdue state identifies management candidates but does not automatically escalate every ticket.

## Dashboards

`DashboardService` obtains role-scoped ticket collections from `TicketService`, applies admin filters server-side, assesses every ticket through `SlaService`, and maps results into dashboard DTOs. It provides:

- Student-owned metrics and pending actions.
- Staff-assigned workload and SLA metrics.
- Admin organization-wide counts, distributions, workload, recent tickets, and filtered views.

The assessment-scale implementation calculates aggregates in memory after a bounded repository read. A larger deployment would move aggregations into database queries or reporting projections.

## Frontend architecture

The frontend is a small hash-routed vanilla JavaScript application:

- `frontend/index.html`: login and application shell.
- `frontend/app.js`: API client, in-memory session, routes, dashboard rendering, ticket detail, comments, pending response, and admin actions.
- `frontend/css/style.css`: shared responsive design system.

User-controlled content is escaped before insertion into HTML. API errors are converted to friendly states and toast messages. JWTs are kept in memory rather than browser storage; refreshing the page requires signing in again.
