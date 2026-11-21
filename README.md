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

## Deploy to Render

The repo has a Render Blueprint (`render.yaml`) that creates two services:

- `opc-fams-api`: the Spring Boot back end, built from `backend/Dockerfile`, in Singapore.
- `opc-fams-web`: the React front end, as a static site.

### 1. Prepare Supabase

1. Use a Supabase project in **Southeast Asia (Singapore)** with the **Data API turned off**. Only the back end
   talks to the database.
2. Database details: **Connect > Direct > Type JDBC > Session pooler**. Note the host, the user
   (`postgres.<project-ref>`) and the database password.
3. Storage key: **Project Settings > API Keys > Secret keys** (or the legacy `service_role` key). This key can do
   anything in the project. It goes only into Render's back-end settings, never into the front end or the repo.
   The back end creates the private bucket `fams-files` itself on first start.

### 2. Create the services

1. Push the code to GitHub.
2. In Render: **New > Blueprint**, connect the GitHub repository, and click **Apply**.
3. Render asks for the values marked `sync: false`:

   | Service | Key | Value |
   |---|---|---|
   | opc-fams-api | `DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` |
   | opc-fams-api | `DB_USER`, `DB_PASSWORD` | from Supabase |
   | opc-fams-api | `CORS_ORIGINS` | the front end's address, e.g. `https://opc-fams-web.onrender.com` |
   | opc-fams-api | `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
   | opc-fams-api | `SUPABASE_SERVICE_KEY` | the secret key from step 1.3 |
   | opc-fams-api | `ADMIN_PASSWORD` | only used when the database has no users yet |
   | opc-fams-api | `PROPERTY_CUSTODIAN_NAME` | optional, printed on issue orders |
   | opc-fams-web | `VITE_API_URL` | the back end's address + `/api`, e.g. `https://opc-fams-api.onrender.com/api` |

   `JWT_SECRET` is generated by Render. If Render added a suffix to a service name, use the real addresses
   shown on each service's page.
4. The first back-end build takes about 5 to 10 minutes. Check `https://<api-address>/api/health`.
5. If you change `VITE_API_URL` later, redeploy the front end: the value is built into its files.

### Free plan notes

- The back end sleeps after 15 minutes without traffic and takes about a minute to wake. Open
  `/api/health` before a demo.
- An unused free Supabase project is paused after a week. Open its dashboard before a demo.
- Files must use Supabase Storage (`STORAGE_TYPE=supabase`, already set in `render.yaml`), because Render's disk
  is wiped on every deploy.

## Backups

`.github/workflows/backup.yml` dumps the database every day at 02:00 (Philippine time) and keeps the dump,
encrypted, as a workflow artifact for 30 days (REQ-405).

**Setup:** in GitHub go to **Settings > Secrets and variables > Actions** and add `BACKUP_DB_HOST`,
`BACKUP_DB_USER`, `BACKUP_DB_PASSWORD` and `BACKUP_PASSPHRASE` (a long random passphrase). Keep a copy of the
passphrase outside GitHub: without it the backups cannot be opened. To run a backup now: **Actions > Daily
database backup > Run workflow**.

**Restore** into an empty test database (PostgreSQL 17 client tools):

```
gpg --decrypt fams-<date>.dump.gpg > fams.dump
pg_restore --no-owner --no-privileges -d "postgresql://<user>@<host>:5432/<test-db>" fams.dump
```

GitHub stops scheduled workflows in a repository with no activity for 60 days. Re-enable it on the Actions tab
if that happens.

## Notes for the group

- Don't commit `.env` files, passwords, or the OPC's real documents (invoices, issue orders,
  masterlists). They contain personal data.
- Database changes go in a new file in `backend/src/main/resources/db/migration` (V5, V6, and so on).
  Don't edit files that are already there.
- The build plan and progress are in `docs/BUILD_PLAN.md`.
