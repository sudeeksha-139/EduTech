# AI Usage Report

## Tools used

- GitHub Copilot conversational coding assistance for architecture, implementation, debugging, documentation, and review.
- Workspace file search, text search, file reads, diagnostics, and patch tools.
- Maven test execution for the Spring Boot backend.
- Node syntax checks for frontend JavaScript.
- A local static HTTP server and integrated browser/Playwright inspection for responsive UI validation.

## Why AI assistance was used

The project was intentionally built as an incremental product-engineering assessment. AI assistance helped translate the requirements into a layered design, implement repetitive controller/service/repository scaffolding, generate focused tests, inspect integration points, and repeatedly validate behavior after each module.

AI was treated as an implementation assistant, not as an authority. The code was repeatedly inspected, tested, and corrected when behavior or assumptions were wrong.

## What AI generated

- Spring Boot project foundation and Maven configuration.
- User, ticket, category, assignment, comment, history, SLA, escalation, pending-action, and dashboard services.
- JWT authentication and Spring Security configuration.
- DTOs, controllers, repositories, validation, and exception handling.
- Demo-profile seed data and idempotence tests.
- Vanilla frontend shell, responsive design system, API client, dashboards, ticket detail, comments, pending response, charts, loading/error/empty states, and toasts.
- Focused unit and MVC security tests.
- Submission documentation.

## Manual review and validation

The implementation was reviewed against the actual controller mappings, DTOs, service methods, security rules, JPA entities, and frontend API calls. The frontend was checked for escaping of user-controlled content, token handling, endpoint/method/body alignment, responsive layout, and missing sample data.

## Bugs and incorrect assumptions discovered

### Incorrect original SLA policy

The initial implementation used `CRITICAL = 4`, `MEDIUM = 72`, and `LOW = 120` hours. The requested policy was corrected to:

- Critical: 8 hours
- High: 24 hours
- Medium: 48 hours
- Low: 72 hours

A centralized `SlaPolicy` and injectable `Clock` were added, with exact-deadline, approaching, overdue, resolved/closed, reopened, and pending-student tests.

### SLA test expectation mismatch

One future-ticket test expected an extra minute of remaining time even though the test supplied a due time exactly eight hours in the future. The assertion was corrected to match the configured fixture.

### Demo seeder invocation issue

The demo seeder was initially a profile-gated component with a `seed()` method that was never called. It was corrected to implement `CommandLineRunner`, with tests exercising the startup hook and idempotence.

### Mockito fixture issues

Several focused tests initially failed because shared Mockito fixtures were unused on early authorization/validation paths. Those were narrowed or marked lenient without weakening production behavior.

### JWT browser persistence

The frontend initially stored the session, including the JWT, in `localStorage`. The final review removed browser persistence and keeps the session in memory only. A refresh therefore requires a new login, which is documented as a limitation.

### Missing lifecycle transition

The documented `NEW -> ASSIGNED` transition was absent from the first policy. It was added and regression-tested.

### Missing CORS configuration

The frontend and backend were separate origins, but no explicit CORS policy existed. Configurable local CORS was added through `FRONTEND_ALLOWED_ORIGINS`.

### Staff authorization gap

The ticket creation service did not reject staff principals directly. Service-layer authorization was added so staff cannot create student tickets, even if frontend controls are bypassed.

### Dashboard filter integration

The backend supported server-side admin filters before the final frontend pass, but the UI did not expose them. The frontend now serializes filter values to the admin dashboard query parameters.

## Security validation

- BCrypt password hashing is used.
- JWTs are validated by a stateless filter.
- Backend service and controller authorization is enforced for role and ownership boundaries.
- Passwords, password hashes, JWTs, and secrets are not included in DTOs or frontend logs.
- Demo seeding remains `@Profile("demo")` and idempotent.
- Real DB credentials and JWT secrets are environment variables and are not documented with values.
- User-controlled ticket/comment/history text is escaped before frontend HTML insertion.
- Validation and malformed request errors return controlled JSON 400 responses.

## Browser and frontend validation

Performed:

- Static frontend served locally.
- Login shell inspected at 1440px, 1024px, 768px, and 390px.
- No horizontal overflow detected.
- No page errors detected during the static browser pass.
- All frontend JavaScript files passed `node --check`.

Not performed:

- Authenticated Playwright credential flow. Credentials were not entered into browser automation.
- Full backend-connected browser smoke flow for student, staff, and admin.

## Backend test result

Final Maven validation:

```text
Tests run: 78
Failures: 0
Errors: 0
Skipped: 0
BUILD: SUCCESS
```

## Limitations

- Final review did not run a real-MySQL integration test.
- Resolution tracking stores status, timestamp, and activity but not a resolution summary/code field.
- Ticket creation does not submit category because the current backend create DTO does not support category input.
- Dashboard aggregation is in-memory and suitable for assessment scale, not an optimized reporting warehouse.
- In-memory frontend authentication requires login again after refresh.
