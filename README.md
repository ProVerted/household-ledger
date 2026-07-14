# Household & Trip Ledger — Phase 1 (Auth Foundation)

This is a runnable skeleton covering Phase 1 of the build plan: registration, login,
JWT access + refresh tokens, a protected route, and the security wiring that Phase 2+
will build on top of.

## Backend (Spring Boot)

### 1. Create the database
```sql
CREATE DATABASE household_ledger;
```
Update `src/main/resources/application.properties` with your actual Postgres
username/password if different from the defaults.

### 2. Generate your own JWT secret (don't ship the placeholder one)
```bash
openssl rand -base64 32
```
Paste the output into `app.jwt.secret` in `application.properties`.

### 3. Run it
```bash
cd backend
./mvnw spring-boot:run
```
The API starts on `http://localhost:8080`.

### 4. Test it with curl
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Jana","email":"jana@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jana@example.com","password":"password123"}'
```
Both return an `accessToken` and `refreshToken`.

## Frontend (Angular)

These files assume you've already scaffolded a project with:
```bash
ng new frontend --standalone --routing --style=css
```
Then drop the contents of the `frontend/src/app` folder in this bundle into your
generated project's `src/app`, overwriting `app.routes.ts` and `app.config.ts`.

Install and run:
```bash
cd frontend
npm install
ng serve
```
Visit `http://localhost:4200` — you'll land on `/login`, can register a new account,
and get redirected to the guarded `/dashboard` route.

## What to verify works end-to-end

1. Register a user → redirected to dashboard, name displayed.
2. Refresh the browser on `/dashboard` → still logged in (token read from localStorage).
3. Log out → redirected to `/login`, and manually navigating to `/dashboard` bounces
   you back to `/login` (authGuard working).
4. Try registering the same email twice → clean 409 error message shown on the form,
   not a raw stack trace (GlobalExceptionHandler working).

## What's deliberately NOT in Phase 1

- Groups, memberships, expenses — that's Phase 2 onward.
- Role-based authorization (`UserPrincipal.getAuthorities()` returns empty for now —
  it starts mattering once GroupMembership roles exist in Phase 2/5).
- Token refresh is naive (single retry on 401). A production version would queue
  concurrent requests during a refresh instead of letting each one refresh independently,
  but that's more complexity than Phase 1 needs to prove the concept.

## Next: Phase 2
Group + GroupMembership entities, the `GroupContextFilter`, and the group switcher UI.

---

# Phase 2 (Groups & Membership)

## New backend pieces
- `Group` + `GroupMembership` entities (table names: `app_group`, `group_membership` -
  `GROUP` is a reserved SQL keyword, hence `app_group`)
- `GroupContextFilter`: reads `/api/groups/{id}/...` from the URL, verifies the
  authenticated user has an **active** membership in that group, and populates a
  request-scoped `GroupContext` bean. Services read the group id from `GroupContext`,
  not from a client-trusted path variable, once past the filter.
- `GroupService` / `GroupController`: create group, list "my groups", add member
  (OWNER/TREASURER only), leave group (deactivates membership rather than deleting it)

## New endpoints to test manually
```bash
# Create a household (replace TOKEN with your accessToken from login)
curl -X POST http://localhost:8080/api/groups \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"name":"Flat 4B","type":"HOUSEHOLD","currency":"USD"}'

# List your groups
curl http://localhost:8080/api/groups/mine -H "Authorization: Bearer TOKEN"

# Add a member (use the group id returned above)
curl -X POST http://localhost:8080/api/groups/1/members \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"email":"otheruser@example.com"}'
```

## Frontend checklist
1. Log in -> dashboard shows "You don't have an active group yet."
2. Click "Manage / switch groups" -> `/groups` page loads (empty state).
3. Click "+ New Group", create a Household -> appears in the list, auto-selected as Active.
4. Create a second group (a Trip, with start/end dates) -> both show in the list;
   clicking one switches which is "Active" instantly (Signal-driven, no page reload).
5. Go back to dashboard -> active group summary reflects whichever you last selected.
6. Log out and back in as the same user -> active group selection persists (localStorage).

## Worth understanding before Phase 3
Try hitting an endpoint like `GET /api/groups/999/members` with a group id you're not
a member of (or that doesn't exist) - you should get a 403, not a 500 or a data leak.
That's `GroupContextFilter` doing its job. This is the mechanism Phase 3's expense
endpoints will sit behind too.

## Next: Phase 3
Expense + ExpenseSplit entities, optimistic locking, and the dynamic split UI.

---

# Phase 3 (Expenses & Splits)

## New backend pieces
- `Expense` entity with `@Version` for optimistic locking, and `ExpenseSplit` for
  the per-member breakdown
- `SplitCalculationService`: the actual split-type logic
  - **EQUAL**: divides evenly, then distributes leftover cents one-by-one across
    the first N people so the splits always sum exactly to the total (naive division
    alone leaves rounding errors)
  - **PERCENTAGE**: validates percentages sum to 100, computes each share, and lets
    the last split absorb any rounding remainder so the total is exact
  - **EXACT**: validates the exact amounts sum to the expense total
- `ExpenseService.updateExpense`: checks the client-supplied `version` against the
  stored one and returns a clean 409 Conflict if they don't match, instead of
  silently overwriting someone else's edit or leaking a raw persistence exception

## Manual test: prove optimistic locking actually works
This is worth doing once, since it's the whole point of Phase 3's hardest piece:
1. `GET /api/groups/{groupId}/expenses/{expenseId}` twice, in two separate tabs/tools
   (e.g. Postman) - note the `version` field is the same in both (e.g. `0`)
2. `PATCH` using the first response's version - succeeds, version is now `1`
3. `PATCH` again using the *first* (now-stale) version `0` - should get a 409, not
   a silent overwrite

## Frontend checklist
1. From the dashboard, click "View expenses" -> empty state for a new group.
2. Click "+ New Expense", fill in description/amount/date, leave Split Type as
   "Equal" -> submit -> appears in the list with an equal split shown per member.
3. Create another expense, switch Split Type to "Percentage" -> percentage inputs
   appear per member. Try submitting with percentages that don't sum to 100 ->
   should show the inline mismatch error and NOT submit.
4. Fix the percentages to sum to 100 -> submits successfully.
5. Try "Exact" split type with amounts that don't match the total -> same inline
   validation behavior.
6. Uncheck everyone -> "select at least one person" error appears.

## Next: Phase 4
Balance calculation + the greedy debt-settlement algorithm - the "wow" feature.

---

# Phase 4 (Balances & Settlement Algorithm)

## New backend pieces
- `BalanceService.computeNetBalances`: sums, per member, everything they paid
  minus everything they owe across all expenses/splits in a group. Positive =
  the group owes them; negative = they owe the group.
- `SettlementCalculationService.suggestSettlements`: the actual algorithm - a
  greedy "largest creditor meets largest debtor" approach using two max-heaps
  (`PriorityQueue`), settling the smaller of the two amounts each round. This
  minimizes the total number of payments needed, rather than naively settling
  every pairwise debt.
- `SettlementTransaction` + confirm/history endpoints: recording that a suggested
  payment actually happened is a separate concern from the balance math itself -
  confirming a settlement does NOT change the net balances, since those reflect
  expense history, not who's paid whom already. (Worth being able to explain this
  distinction if asked - it's a deliberate design choice, not an oversight.)

## New endpoints
```
GET  /api/groups/{groupId}/balances
GET  /api/groups/{groupId}/settlements/suggested
POST /api/groups/{groupId}/settlements/confirm
GET  /api/groups/{groupId}/settlements/history
```

## Frontend checklist
1. From the dashboard, click "Balances & settlements".
2. With a few expenses already added (from Phase 3 testing) across 2+ members,
   confirm the Net Balances section shows sensible numbers - whoever paid more
   than their share should show "is owed", whoever paid less should show "owes".
3. Check Suggested Settlements - with only 2 members this is trivially one line,
   but the algorithm's value shows with 3+: try adding a third member and a few
   more expenses with different payers, and confirm the number of suggested
   payments stays minimal (e.g. 2 payments settling 3 people, not 3+).
4. Click "Mark as Paid" on a suggestion -> appears in Settlement History below.
   Refresh the page - the net balances should be unchanged (by design, per above),
   but the history entry should persist.

## The one thing worth being able to explain out loud
Given 3 people where A is owed $30, B is owed $10, and C owes $40 total - a naive
approach might generate 2 separate payments from C (one to A, one to B). The greedy
algorithm here does exactly that in this case, but with more complex multi-way debts
(4+ people, mixed credits/debits) the greedy match-largest-with-largest approach
provably produces fewer total transactions than settling every pairwise relationship
that technically exists on paper.

## What's next
Phases 5 and 6 from the original plan (approval workflows, recurring expenses) are
the remaining stretch goals - by this point you have a genuinely complete, demoable
core: auth, dynamic multi-tenancy, expense splitting with real algorithmic split
logic, optimistic locking, and a debt-settlement algorithm. That's a strong stopping
point if you're presenting soon, with room to keep building if you have time.

---

# Phase 5 (Authorization Depth & Approval Workflow)

## New backend pieces
- `CustomPermissionEvaluator`: implements Spring Security's `PermissionEvaluator`
  interface. Lets you write `@PreAuthorize("hasPermission(#groupId, 'group', 'APPROVE_EXPENSE')")`
  on a controller method instead of a manual role check buried in a service - the
  permission-to-role mapping lives in one small `Map` at the top of the class.
- `MethodSecurityConfig`: registers the evaluator via `@EnableMethodSecurity` and a
  `DefaultMethodSecurityExpressionHandler` bean - this is the wiring step that's
  easy to forget and silently makes `hasPermission(...)` always return false.
- `Group.approvalThreshold`: expenses above this amount land in `PENDING_APPROVAL`
  instead of auto-approving; a treasurer/owner has to explicitly approve or reject.
- Only `APPROVED` expenses count toward balances now - pending and rejected ones
  are excluded from `BalanceService`.

## Worth being able to explain
This project now has *two* authorization patterns living side by side on purpose:
- **Phase 2's manual `requireRole()`** in `GroupService` (imperative, simple, easy
  to read for straightforward cases)
- **Phase 5's `@PreAuthorize("hasPermission(...))")`** (declarative, centralizes the
  permission-to-role mapping, scales better as more permission types get added)

Being able to say "I used the simpler pattern where it was enough, and the more
structured one where the codebase would benefit from centralizing the rules" is a
stronger answer than pretending only one pattern exists.

## Manual test
1. Create a group with a low approval threshold (e.g. 10.00).
2. Add an expense above that amount -> its status shows `PENDING_APPROVAL` in the
   list, with no split effect on balances yet.
3. As the OWNER, click "Approve" -> status flips to `APPROVED`, and it now appears
   in balance calculations.
4. Log in as a plain `MEMBER` (not owner/treasurer) in that same group - the
   Approve/Reject buttons shouldn't even render, and hitting the endpoint directly
   (e.g. via curl) should return a 403 with a clear JSON message.

## Next: Phase 6
Recurring expenses and notifications - the feature-complete finish line.

---

# Phase 6 (Recurring Expenses & Polish) — Implemented

## New backend pieces
- `RecurringExpenseTemplate` entity: description, amount, split rule, `dayOfMonth`,
  `active` flag, plus `lastGeneratedAt` so the scheduler knows what it's already produced.
- `RecurringExpenseScheduler` (`@Scheduled`): runs daily, finds active templates whose
  `dayOfMonth` matches today (clamped to the last day of shorter months - e.g. a
  `dayOfMonth` of 31 fires on Feb 28/29), and hands off to...
- `RecurringExpenseGenerationService`: turns a template into a real `Expense` +
  `ExpenseSplit` rows for that month, respecting the same approval-threshold logic
  Phase 5 introduced (a generated rent expense above the threshold still lands in
  `PENDING_APPROVAL`, it isn't silently auto-approved just because it's automatic).
- `RecurringExpenseService` / `RecurringExpenseController`: create/deactivate templates,
  list active templates for a group (OWNER/TREASURER only for create/deactivate, same
  permission pattern as Phase 5's `hasPermission(...)`).
- **Notifications** via Spring Events (decoupled from the write path on purpose):
  - `NotificationEvent` is published wherever something notification-worthy happens
    (new expense added, expense approved/rejected, recurring expense auto-generated).
  - `NotificationEventListener` is `@Async` (wired through `AsyncConfig`) - so posting
    an expense doesn't block on writing notification rows for every group member.
  - `Notification` entity + `NotificationService` / `NotificationController`: list mine,
    unread count, mark-as-read.

## New endpoints
```
POST /api/groups/{groupId}/recurring          -> create a recurring template
GET  /api/groups/{groupId}/recurring           -> list templates for the group
PATCH /api/groups/{groupId}/recurring/{id}/deactivate
GET  /api/notifications/mine
GET  /api/notifications/unread-count
POST /api/notifications/{id}/read
```

## Frontend checklist
1. Go to a group -> "Recurring expenses" -> "Set up a recurring expense" (OWNER/
   TREASURER only) -> fill in description/amount/day-of-month/payer -> save.
2. Confirm it appears in the table with "Active" status and "Never" under
   Last generated (nothing's run yet).
3. Dashboard now shows a notification bell. Add a regular expense as a different
   group member (or via curl as another user) -> the badge count increments without
   a page reload (polled every 30s).
4. Click the bell -> panel lists notifications, unread ones are visually distinct;
   clicking one marks it read and the badge count drops.
5. As a non-owner/treasurer member, confirm the "Set up a recurring expense" button
   and the per-row "Deactivate" action don't render, and hitting those endpoints
   directly returns a 403.

## Worth being able to explain
Why notifications are event-driven instead of a direct service call: `ExpenseService`
shouldn't need to know who's subscribed to "an expense was created" - it just publishes
the fact. Today that's a notification-row writer; tomorrow it could just as easily be
an email digest or the optional WebSocket push from the stretch goals, added as a new
listener with zero changes to `ExpenseService` itself. That's the actual argument for
Spring Events here, not just "it's a pattern I know."

## What's still open (stretch goals, not started)
- WebSocket/STOMP live push (notifications are polled today, not pushed)
- Multi-currency conversion (currency is stored per-group/per-expense but never
  converted - a balance sheet with mixed currencies will silently misreport)
- Docker Compose + deployment write-up

## Milestone
This closes the build plan end-to-end: auth, dynamic multi-tenancy, expense splitting
with real split-type logic, optimistic locking, a debt-settlement algorithm, layered
authorization, and now recurring expenses with async notifications. Feature-complete
per the original spec.

---

# UI Design Pass

The frontend has been restyled around a single idea: this is a ledger, not a dashboard
- the one job of the UI is "let me see who owes what and act on it fast."

- **Palette**: `#F4F5F1` paper background, `#17241F` ink for text, `#1F6F5C` emerald
  as the primary action color, `#E4572E` coral for anything needing attention
  (pending approval, validation errors), and a dedicated owed/owing pair
  (`#2F9E44` / `#C0392B`) used consistently across balances and settlements.
- **Type**: Space Grotesk for headings (used sparingly - page titles, card names),
  Inter for body/UI copy, and JetBrains Mono for every money figure, so amounts
  align the way real ledger columns do.
- **Signature moment**: the settlement suggestion is now a torn-ticket-style card
  (`.settlement-row` / `.ticket-*` classes in `balance-summary.component.html`) with
  a directional thread running from the debtor (coral end) to the creditor (green
  end), rather than a plain "Sam → Alex: $42" line.
- All shared tokens and component styles live in `frontend/src/styles.css`; no
  per-component CSS files were introduced, so existing component class names
  (`.expense-item`, `.group-item`, `.balance-list`, etc.) needed no template
  changes beyond the settlement-ticket markup.




