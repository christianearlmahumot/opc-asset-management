# OPC Fixed-Asset Management System

A web system for the Inventory Section of the CIT-U **Office of the Property Custodian (OPC)**. It replaces
the Excel masterlist with one database for the university's fixed assets.

Capstone project of Team 2627-sem1-it332-09, College of Computer Studies, CIT-U.
The requirements and design are in [docs/](docs/): the SRS and SDD `.docx` files. The build plan and what is
finished so far are in [docs/BUILD_PLAN.md](docs/BUILD_PLAN.md).

| Part | Technology |
|---|---|
| Front end | React 18 + Vite, in [frontend/](frontend/) |
| Back end | Java 17, Spring Boot 3.5, in [backend/](backend/) |
| Database | PostgreSQL (Supabase, or local Docker) |

---

## 1. What to install first

| Tool | Version | Check with | Get it from |
|---|---|---|---|
| **Git** | any | `git --version` | https://git-scm.com |
| **Java JDK** | **17** | `java -version` | https://adoptium.net (Temurin 17) |
| **Node.js** | 20 or newer | `node --version` | https://nodejs.org (LTS) |
| Docker Desktop | *only for option C below* | `docker --version` | https://www.docker.com |

You do **not** need to install Maven. The project includes the Maven wrapper (`mvnw` / `mvnw.cmd`),
which downloads it on the first run.

---

## 2. Get the code

```powershell
git clone https://github.com/BrentSanico1/asset-management-system.git
cd asset-management-system
```

---

## 3. Choose a database

You do **not** create any tables yourself. When the back end starts, it creates every table and the
starting data automatically (the SQL files are in
[backend/src/main/resources/db/migration](backend/src/main/resources/db/migration)).

Pick **one** option:

### Option A: the group's shared Supabase database (easiest)
Everyone sees the same data. Ask the project owner to **send you the `DB_URL`, `DB_USER` and
`DB_PASSWORD` privately** (chat or DM, never in the repo or a group post).

### Option B: your own Supabase project
1. Create a free project at https://supabase.com and choose the **Southeast Asia (Singapore)** region.
2. Click **Connect** and choose **Session pooler**. Copy the host, port, database and user.
   - Do not use the **Direct connection**. It only works on networks with IPv6.
   - Do not use the **Transaction pooler** (port 6543). It breaks the database setup.
3. Your `DB_URL` looks like
   `jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require`
   and your `DB_USER` looks like `postgres.abcdefghijk`.

### Option C: a local database in Docker
Start Docker Desktop, then from the project folder:
```powershell
docker compose up -d
```
This runs PostgreSQL on `localhost:5432` (database, user and password are all `fams`). The back end
uses these values by default, so you can leave the `DB_` lines out of your `.env`.

---

## 4. Create your `backend/.env`

```powershell
copy backend\.env.example backend\.env
```
Open `backend/.env` and fill in:
- `DB_URL`, `DB_USER`, `DB_PASSWORD`: from step 3 (delete them for option C)
- `JWT_SECRET`: any random text, **at least 32 characters**
- `ADMIN_PASSWORD`: the password for the first Administrator account (8+ characters)

Notes:
- **On the shared database (option A)**, the admin account already exists. Your `ADMIN_` values are
  ignored, so ask the project owner for an account instead (see section 7).
- **On a new database**, the admin account is created **once**, on the first start. Set
  `ADMIN_PASSWORD` **before** you start the back end the first time. Changing it later does nothing.
- `backend/.env` is git-ignored. **Never commit it.**

---

## 5. Run the back end

Open a terminal in VS Code (`` Ctrl+` ``):
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
- On macOS or Linux, use `./mvnw spring-boot:run`.
- Run it **from inside `backend`**, because that is where it reads `.env`.
- The first run takes a few minutes while dependencies download.
- It is ready when you see `Started FamsApiApplication`.
- Check it in a browser: http://localhost:8080/api/health should show `{"status":"ok"}`.

Keep this terminal open.

---

## 6. Run the front end

Open a **second** terminal:
```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```
- `npm install` is only needed the first time, and after someone adds a new package.
- On Windows PowerShell, use `npm.cmd`, because plain `npm` is often blocked by the script policy.
  On macOS or Linux, use `npm`.

Open **http://localhost:5173**.

To stop either one, press `Ctrl+C` in its terminal.

---

## 7. Log in and try it

1. Log in with the Administrator account: `admin` and the `ADMIN_PASSWORD` from your `.env`, or the
   account the project owner gave you.
2. To test the other roles, go to **Users → + Add User** and create one account per role. New users get
   a temporary password and must choose their own at first login.

| Role | Can do |
|---|---|
| Administrator | Everything, including users, departments, categories and the audit log |
| Property Staff | Assets, issue orders, movements, reports |
| NAS Assistant | Encode assets as **drafts** for Property Staff to confirm; search |
| Department Head | View their **own department's** assets only |

**A quick walk-through:**
1. **Asset Register → Departments:** add a department, e.g. `MEDTECH`.
2. **Asset Register → Accountable Persons:** add a person in that department.
3. **Asset Register → + Add Asset:** add an item with no department, so it goes to OPC storage.
4. **Issue Orders → + New Request:** create a request for the department, then **Prepare Issue Order**,
   tick the item, **Confirm and Generate**, then **Print**.
5. Open the asset. Its history shows *Encoded* and then *Issued*, and its record shows the IO number.
6. On the asset, try **Transfer** and **Condition / Turnover**, then **Movements → Resignation Turnover**.

---

## 8. Common problems

| Problem | Fix |
|---|---|
| Login page says **"Cannot reach the server"** | The back end is not running, or it is still starting. Check terminal 1. |
| `password authentication failed` | `DB_PASSWORD` or `DB_USER` in `backend/.env` is wrong. |
| `Connection refused`, `UnknownHost` or a timeout to Supabase | Use the **Session pooler** connection string (section 3, option B). |
| `Port 8080 already in use` | Another back end is still running. Close it, or add `PORT=8081` to `.env` and change the port in `frontend/vite.config.js`. |
| `JWT_SECRET must be at least 32 characters` | Make `JWT_SECRET` longer. |
| Admin login fails on a new database | The admin account was created with an old `ADMIN_PASSWORD`. Ask the project owner to reset it. |
| A page says **"This feature is not available on the server"** | Your back end is older than your front end. Pull the latest code and restart the back end. |
| `npm` "running scripts is disabled" | Use `npm.cmd` instead of `npm`. |
| `mvnw.cmd` is not recognized | Run it from inside the `backend` folder, with the leading `.\`. |

After you `git pull` new code:
- **Restart the back end.** New database changes apply automatically on start.
- **Run `npm.cmd install`** in `frontend` if `package.json` changed.

---

## 9. Project layout

```
backend/                      Spring Boot API, one package per module
  src/main/java/edu/citu/opc/fams/
    access/       login, users, roles           (Module 6)
    audit/        audit log                     (Module 6)
    reference/    departments, people, categories, units
    asset/        asset register                (Module 2)
    movement/     transfers, turnovers          (Module 4)
    issueorder/   requests, issue orders, PDF   (Module 3)
  src/main/resources/
    application.yml           settings (secrets come from backend/.env)
    db/migration/             database tables (Flyway; never edit an applied file, add a new one)
frontend/                     React app
  src/app/                    routes and sidebar
  src/shared/                 API client, layout, UI components
  src/modules/<module>/       screens per module
docs/                         SRS, SDD, proposal, build plan
docker-compose.yml            optional local PostgreSQL
```

---

## 10. Rules for the group

- **Never commit `backend/.env`** or any password, token or connection string.
- **Do not commit the OPC's real records** (invoices, issue orders, masterlists, photos). They contain
  personal data covered by the Data Privacy Act of 2012.
- **Database changes go in a new migration file** (`V5__...sql`, `V6__...sql`). Never edit a file that
  has already been applied: everyone's database would stop matching.
- On the **shared** database, your test data is visible to everyone. Name it clearly, e.g. a
  description starting with `TEST`.
