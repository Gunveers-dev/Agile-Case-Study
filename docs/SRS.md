# Software Requirements Specification — LibraTrack

## 1. Introduction

**Purpose.** This document specifies the requirements of LibraTrack, a web-based library management system for a college library. It is the output of Sprint 1.

**Scope.** LibraTrack lets a library administrator manage the book catalogue and members, issue and return books, calculate overdue fines automatically, review transaction history and view analytics. Members do not log in; all actions are performed by the administrator at the circulation desk.

**Definitions.** *Title* – a catalogue entry (one ISBN). *Copy* – one physical book of a title. *Loan / transaction* – one copy issued to one member. *Overdue* – a loan not returned after its due date.

## 2. Overall description

| Item | Description |
|---|---|
| Users | Library administrator (single role: ADMIN) |
| Environment | Any modern browser; server needs Java 17+ |
| Data store | H2 database in file mode (data persists across restarts) |
| Constraints | Java/Spring Boot back end only (no React/Angular/Node/Firebase); responsive UI |
| Assumptions | Fines are collected at the desk when the book is returned |

## 3. Functional requirements

### 3.1 Authentication
* **FR-1** The system shall require the administrator to sign in with email and password before any page other than the login page can be opened.
* **FR-2** Passwords shall be stored as BCrypt hashes.
* **FR-3** The administrator shall be able to sign out, use *Remember me*, and change the password.

### 3.2 Dashboard
* **FR-4** The dashboard shall show total books (copies), total members, books issued, available copies, overdue books and pending fines, all computed from the database.
* **FR-5** The dashboard shall show the monthly issue trend for six months, recent activities, the most popular books and quick-action links.

### 3.3 Book management
* **FR-6** Add, edit, view and delete books with fields: book ID, title, author, ISBN, category, publisher, total quantity, available copies, created date.
* **FR-7** Search by title, author, ISBN or book ID; filter by category and availability; paginate results.
* **FR-8** Show a status badge: Available, Low Stock (1–2 copies left), Issued (all copies on loan), Out of Stock (no copies on the shelf and none on loan).
* **FR-9** Validation: ISBN unique and 10/13 digits, title required, quantity > 0, available ≤ total. A book with active loans cannot be deleted.

### 3.4 Member management
* **FR-10** Register, edit, view and delete members with fields: member ID, full name, email, phone, department, registration date.
* **FR-11** Search members; view a member profile with borrowing history.
* **FR-12** Validation: member ID unique, email valid and unique, phone 10 digits, required fields present. A member with active loans cannot be deleted.

### 3.5 Circulation
* **FR-13** Issue a book by selecting a member, a book, an issue date and a due date (default: issue date + 14 days).
* **FR-14** Only registered members may borrow; only titles with an available copy may be issued; a member may hold at most 3 books and not two copies of the same title.
* **FR-15** Issuing reduces the available quantity by one and creates a transaction.
* **FR-16** List currently issued books, searchable by member ID, member name or book title, showing due date, overdue status and the fine if returned today.
* **FR-17** Returning a book records the return date, marks the loan RETURNED, increases the available quantity, calculates and displays the fine. A loan cannot be returned twice.

### 3.6 Fines
* **FR-18** Fine = overdue days × ₹5. No fine if returned on or before the due date. Invalid or missing dates are rejected.

### 3.7 Transactions and reports
* **FR-19** Transaction history with ID, member, book, issue/due/return dates, fine and status (ISSUED, OVERDUE, RETURNED); search, status filter, date filter, newest-first sorting.
* **FR-20** Reports with totals, monthly issues, most-borrowed books, overdue books, total fines collected and charts, plus a print-friendly layout.

## 4. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 Usability | Consistent layout, confirmation before destructive actions, toast feedback, inline validation messages, empty states. |
| NFR-2 Responsiveness | Usable on laptop and tablet widths; sidebar collapses on small screens. |
| NFR-3 Security | Server-side authentication, CSRF protection on forms, hashed passwords, all routes protected. |
| NFR-4 Reliability | Business rules enforced in the service layer inside transactions; data persists across restarts. |
| NFR-5 Maintainability | Layered architecture (Controller → Service → Repository), constructor injection, DTOs, central exception handling. |
| NFR-6 Testability | Business logic covered by automated JUnit 5 / Mockito tests; integration tests for persistence, security and pages. |
| NFR-7 Portability | Runs anywhere Java 17 runs; works offline after the first build (bundled UI fallbacks). |

## 5. Use cases (summary)

| Use case | Main flow | Alternative flows |
|---|---|---|
| UC-1 Sign in | Enter email/password → dashboard | Wrong credentials → error on login page |
| UC-2 Add book | Fill form → saved → details page | Duplicate ISBN / invalid fields → form shows errors |
| UC-3 Register member | Fill form → saved → profile page | Duplicate ID / invalid email → form shows errors |
| UC-4 Issue book | Pick member & book → issued → stock − 1 | Member at limit / book unavailable → reason shown |
| UC-5 Return book | Click *Return* → confirm → receipt with fine | Already returned → error toast |
| UC-6 View reports | Open Reports → charts → *Print report* | — |

## 6. Data model

See the ER diagram in the README (§4). Entities: **Book**, **Member**, **Loan** (many-to-one to Book and Member), **Admin**.
