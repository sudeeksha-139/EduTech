# AI Usage Prompts

This record summarizes the actual development prompts used in the EduSupport development conversation. It preserves the request intent without reproducing credentials or secrets.

## 1. Requirements and architecture

- “I am building a product-engineering assessment project called EduSupport … Before generating any implementation code, analyze the problem and provide … Product Requirements … Ticket Lifecycle … REST API Design … UI/UX Plan … Edge Cases … Trade-offs.”
- “lets start building”

These prompts established the product scope, roles, lifecycle, SLA model, API boundaries, and the decision to build incrementally.

## 2. Backend foundation

- “Continue the EduSupport project from the current implementation … The next build slice is … Java 17 / Spring Boot 3 … ticket and user domain models … REST health endpoint.”

This led to the Maven Spring Boot foundation, domain enums, JPA entities, repositories, health endpoint, and first workflow tests.

## 3. JWT and RBAC

- “Continue the existing EduSupport project … Implement JWT Authentication + Role-Based Authorization … Student, Staff, Admin permissions … POST /api/auth/login … BCrypt … JWT filter … Spring Security 6 … Add tests for valid login, invalid password, unknown user, missing/invalid JWT, ownership, staff authorization, and admin access.”

## 4. Demo data

- “Implement safe development/demo seed data … Create demo users … categories … realistic ticket data … idempotent … profile-gated … verify startup, no duplicates, BCrypt, and login.”
- Follow-up debugging prompt identifying that `DemoDataSeeder.seed()` was never invoked at startup.
- “Fix this properly … implement CommandLineRunner or ApplicationRunner … preserve @Profile(\"demo\") … add/update tests proving the seeder is correctly wired.”

## 5. Assignment and ownership

- “Implement the Ticket Assignment and Ownership module … ADMIN can assign/reassign … STAFF assigned tickets … TicketHistory … DTOs … focused tests for assignment, reassignment, unauthorized roles, invalid IDs, and history.”

## 6. Comments and history

- “Implement the Ticket Comments and Activity History module … extend the existing TicketHistory implementation … TicketComment entity/repository … chronological comments … status/comment/creation history … focused tests.”

## 7. SLA

- “Implement the SLA Ageing, Overdue Detection, Pending-Action and Escalation module … CRITICAL = 8 hours, HIGH = 24 hours, MEDIUM = 48 hours, LOW = 72 hours … injectable UTC Clock … overdue/approaching/active-state tests.”

## 8. Pending workflow

- “Implement Pending-Action Workflow … PENDING_STUDENT explicit waiting state … staff reason … student response through existing comment/activity mechanism … return to IN_PROGRESS … do not silently change SLA behavior.”

## 9. Dashboard

- “Implement the next module: Dashboard and Management Visibility APIs … student, staff, admin dashboard endpoints … server-side filters … use existing SlaService … DTOs … empty states … focused dashboard and authorization tests.”

## 10. Frontend

- “IMPORTANT: FRONTEND VISUAL QUALITY IS A HIGH PRIORITY … create a polished modern SaaS product … Bootstrap 5 … login page … dashboards … SLA visualization … ticket details … create ticket … Chart.js … responsive design … real backend APIs … no fake functionality.”

## 11. Final security and integration review

- “Perform a final end-to-end engineering review of the complete EduSupport application … inspect authentication, RBAC, lifecycle, assignment, comments, pending workflow, SLA, escalation, dashboards, frontend API integration, frontend security, UI quality, database, errors, performance, assessment requirements, and testing.”

The final review also requested concrete fixes only, a complete Maven run, frontend syntax checks, and responsive browser validation.
