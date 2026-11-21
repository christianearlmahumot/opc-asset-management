# OPC Fixed-Asset Management System

Capstone project for the CIT-U Office of the Property Custodian.
React frontend, Spring Boot backend, PostgreSQL database.

## Requirements

- Git
- Java 17 (Temurin: https://adoptium.net)
- Node.js 20 or newer
- Docker Desktop (only if you want a local database)

Maven is not needed. The project comes with the Maven wrapper.

## Setup

### 1. Clone the repo

```
git clone https://github.com/BrentSanico1/asset-management-system.git
cd asset-management-system
```

### 2. Set up a database

Use one of these:

- **Shared Supabase database.** Ask Brent for the connection details. They are sent privately and never
  put in the repo.
- **Your own Supabase project.** Create a project in the Singapore region, click Connect, and copy the
  **Session pooler** connection. Don't use the Direct connection or the Transaction pooler.
- **Local database.** With Docker Desktop running, run `docker compose up -d` in the project folder.

You don't need to create any tables. The backend creates them the first time it starts.

### 3. Create backend/.env

```
copy backend\.env.example backend\.env
```

Open `backend/.env` and fill in:

- `DB_URL`, `DB_USER`, `DB_PASSWORD`: your database details. If you use the local Docker database,
  delete these three lines.
- `JWT_SECRET`: any random text, at least 32 characters.
- `ADMIN_PASSWORD`: the password for the first admin account.

The admin account is only created the first time the backend runs on an empty database, so set
`ADMIN_PASSWORD` before starting it. On the shared database the admin already exists, so ask Brent for an
account instead.

Don't commit `backend/.env`.

### 4. Run the backend

```
cd backend
.\mvnw.cmd spring-boot:run
```

On Mac or Linux, use `./mvnw spring-boot:run`.

The first run takes a few minutes. It's ready when it prints `Started FamsApiApplication`. You can also
check http://localhost:8080/api/health, which should show `{"status":"ok"}`.

### 5. Run the frontend

In a second terminal:

```
cd frontend
npm.cmd install
npm.cmd run dev
```

On Mac or Linux, use `npm` instead of `npm.cmd`.

Open http://localhost:5173 and log in as `admin`.

## Testing other roles

Log in as admin, go to Users, and add an account for each role: Property Staff, NAS Assistant and
Department Head. New accounts get a temporary password and must change it on first login.

A quick test flow:

1. In Asset Register, open Departments and add a department.
2. Open Accountable Persons and add a person in that department.
3. Add an asset with no department, so it goes to OPC storage.
4. In Issue Orders, create a request, then Prepare Issue Order, select the asset, and print it.
5. Open the asset and try Transfer and Condition / Turnover.

## After pulling new changes

- Restart the backend. Any new database changes apply automatically.
- Run `npm.cmd install` in `frontend` if `package.json` changed.

## Common problems

- **"Cannot reach the server" on the login page.** The backend isn't running yet.
- **`password authentication failed`.** Check `DB_USER` and `DB_PASSWORD` in `backend/.env`.
- **Can't connect to Supabase.** Use the Session pooler connection, not the Direct one.
- **Port 8080 already in use.** Another backend is still running. Close it first.
- **`npm` scripts are disabled.** Use `npm.cmd` instead of `npm`.
- **A page says the feature is not available on the server.** Pull the latest code and restart the
  backend.

## Notes for the group

- Don't commit `.env` files, passwords, or the OPC's real documents (invoices, issue orders,
  masterlists). They contain personal data.
- Database changes go in a new file in `backend/src/main/resources/db/migration` (V5, V6, and so on).
  Don't edit files that are already there.
- The build plan and progress are in `docs/BUILD_PLAN.md`.
