# Agile Plan — LibraTrack

Methodology: **Scrum**, five one-week sprints. Each sprint starts with planning (stories pulled from the product backlog), runs short daily stand-ups, and ends with a review (demo of working software) and a retrospective.

## Roles

| Role | Responsibility |
|---|---|
| Product Owner | Owns and prioritises the backlog (the course brief acts as the customer). |
| Scrum Master | Keeps the process running, removes blockers. |
| Development Team | Designs, builds and tests each increment. |

## Definition of Done

A story is *done* when: the code compiles; business rules are in the service layer; unit tests for the rules pass (`mvn clean test`); the page works end-to-end in the browser with validation and feedback; the UI follows the LibraTrack design; the README is updated where relevant.

## Product backlog (user stories)

Story points use the Fibonacci scale.

| ID | User story | Acceptance criteria | Pts | Sprint |
|---|---|---|---|---|
| US-01 | As an admin I want to sign in securely so that only staff can use the system. | Wrong password rejected; all pages redirect to login when signed out; password stored hashed. | 5 | 2 |
| US-02 | As an admin I want a dashboard so that I see the state of the library at a glance. | Six live statistic cards; issue-trend chart; recent activity; popular books; quick actions. | 8 | 2 |
| US-03 | As an admin I want to add, edit and delete books so that the catalogue stays accurate. | Unique ISBN; title required; quantity > 0; available ≤ total; delete blocked while on loan. | 8 | 2 |
| US-04 | As an admin I want to search and filter books so that I find titles quickly. | Search by title/author/ISBN/ID; filter by category & availability; pagination; status badges. | 5 | 2 |
| US-05 | As an admin I want to register and maintain members so that only known people borrow. | Unique member ID & email; valid email/phone; profile with history; delete blocked while borrowing. | 8 | 3 |
| US-06 | As an admin I want to issue a book so that a member can borrow it. | Registered members only; available copies only; max 3 books; default due date +14 days; stock − 1; transaction saved. | 8 | 3 |
| US-07 | As an admin I want to return a book so that it is back in stock. | Return date saved; status RETURNED; stock + 1; duplicate return refused. | 5 | 3 |
| US-08 | As an admin I want fines calculated automatically so that late returns are charged fairly. | ₹5 × overdue days; ₹0 on/before due date; fine shown on return. | 3 | 4 |
| US-09 | As an admin I want a transaction history so that I can trace any loan. | ID, member, book, dates, fine, status; search; status & date filters; newest first. | 5 | 4 |
| US-10 | As an admin I want reports so that I can present library usage. | Totals, monthly issues, most borrowed, overdue list, fines collected, charts, print view. | 5 | 4 |
| US-11 | As a developer I want automated tests so that changes do not break the rules. | JUnit 5 + Mockito tests for fine, book, member, circulation, dashboard services; integration tests pass. | 8 | 5 |
| US-12 | As a presenter I want sample data and an offline-safe UI so that the viva demo always works. | ≥15 books, ≥10 members, ≥5 loans inserted once; pages work without internet. | 3 | 5 |

## Sprints

### Sprint 1 — Requirements & design
* Requirement gathering from the brief; SRS (`docs/SRS.md`).
* Database planning: entities Book, Member, Loan, Admin and their relationships (ER diagram in README).
* Architecture: Controller → Service → Repository → H2; package structure; UI direction from the reference design (palette, typography, layout).
* **Review:** SRS, ER model and UI style approved. **Retro:** agree on Definition of Done.

### Sprint 2 — Secure core (US-01 … US-04, 26 pts)
* Spring Security login with BCrypt and the demo admin; protected routes.
* Dashboard with live statistics and Chart.js trend.
* Book CRUD, search, filters, badges, validation.
* **Review:** login → dashboard → add/edit/delete book demonstrated.

### Sprint 3 — Members & circulation (US-05 … US-07, 21 pts)
* Member CRUD with profile and borrowing history.
* Issue Book tab with all lending rules; Return Book tab with search.
* **Review:** issue and return demonstrated, including the 3-book limit.

### Sprint 4 — Fines & insight (US-08 … US-10, 13 pts)
* `FineService` (₹5/day) wired into returns and pending-fine figures.
* Transaction history with filters; Reports page with charts and print view.
* **Review:** late return shows the correct fine; reports printed.

### Sprint 5 — Quality (US-11, US-12, 11 pts)
* Unit tests (55) for all business rules; Spring Boot integration tests (persistence, security, every page, forms).
* Bug fixing found by the tests and by UI review; offline fallbacks; README.
* **Review:** `mvn clean test` green; full demo run-through.

## Velocity

| Sprint | Committed pts | Completed pts |
|---|---|---|
| 1 | design work (not pointed) | — |
| 2 | 26 | 26 |
| 3 | 21 | 21 |
| 4 | 13 | 13 |
| 5 | 11 | 11 |
