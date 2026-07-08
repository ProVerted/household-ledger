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
