# Cloud Data Guard

**Cloud-Based Data Redundancy Removal and Validation System** is a Java/Spring Boot application that accepts validated records, detects duplicate submissions, and stores each verified record only once. The browser app, API, and MySQL database are designed to run together locally or on AWS.

> This repository is cloud-ready, not cloud-deployed. No AWS resources are created by the source code or by these instructions.

## What it does

- Registers users and authenticates them with BCrypt-hashed passwords.
- Gives new accounts the `USER` role. Users can submit records but cannot browse the shared data or reports.
- Gives administrators the management dashboard, search/filter, edit/delete, duplicate log, and CSV report.
- Checks a unique external ID and a SHA-256 fingerprint of normalized record content before saving.
- Keeps rejected duplicate submissions in a separate audit table; rejected content is not inserted into the main records table.
- Enforces uniqueness again with database constraints, so application checks are not the only line of defense.
- Validates request fields in both the browser and the backend.
- Uses Spring Security sessions, CSRF tokens, BCrypt, and Spring Data JPA parameterized operations.

The fingerprint compares normalized name, email, category, and details. It trims surrounding whitespace, collapses repeated whitespace, and ignores letter case. Similar-but-not-identical records are not treated as duplicates; this is exact normalized matching, not fuzzy matching.

## Project structure

```text
.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/codealpha/dataguard/
│   │   │   ├── controller/       # REST API and API error responses
│   │   │   ├── dto/              # API request and response types
│   │   │   ├── entity/           # JPA entities
│   │   │   ├── repository/       # Spring Data JPA repositories
│   │   │   ├── security/         # Authentication and authorization
│   │   │   └── service/          # Validation, registration, deduplication
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── schema.sql        # Reference schema; run manually if desired
│   │       └── static/           # HTML, CSS, JavaScript dashboard
│   └── test/
│       ├── java/                 # Deduplication and password tests
│       └── resources/            # Isolated H2 test profile
└── .env.example                  # Local Docker settings template only
```

## Run locally

### Requirements

- Java 17 or newer
- Maven 3.9 or newer
- MySQL 8.0 or newer, or Docker Desktop with Docker Compose

### Option A: MySQL installed on your machine

1. Create a database and an application user. Use a distinct password for your machine:

   ```sql
   CREATE DATABASE data_guard CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'data_guard'@'localhost' IDENTIFIED BY 'replace-with-a-long-local-password';
   GRANT ALL PRIVILEGES ON data_guard.* TO 'data_guard'@'localhost';
   ```

2. In PowerShell, set the connection values and bootstrap an administrator. The bootstrap password must be at least 12 characters. Do not put real passwords in source control.

   ```powershell
   $env:DB_URL = 'jdbc:mysql://localhost:3306/data_guard?serverTimezone=UTC'
   $env:DB_USERNAME = 'data_guard'
   $env:DB_PASSWORD = 'replace-with-a-long-local-password'
   $env:APP_BOOTSTRAP_ADMIN_USERNAME = 'admin'
   $env:APP_BOOTSTRAP_ADMIN_PASSWORD = 'replace-with-a-strong-admin-password'
   mvn spring-boot:run
   ```

3. Open `http://localhost:8080`. Sign in with the bootstrap administrator. Use **Create account** for standard user accounts.

On first startup, Hibernate creates or updates the tables. The `schema.sql` file is a reference/manual setup script; automatic SQL initialization is disabled to avoid requiring database-creation privileges from the application account.

### Option B: Docker Compose

1. Copy `.env.example` to `.env` and replace every example password with a strong local value. `.env` is ignored by Git.
2. From the project root, run:

   ```powershell
   docker compose up --build
   ```

3. Visit `http://localhost:8080`. Stop the services with `Ctrl+C`; use `docker compose down` to stop and remove containers. The named `mysql_data` volume is retained unless explicitly removed.

The included example credentials are for local setup only. Never reuse them in AWS or a public environment.

### Run tests

```powershell
mvn test
```

Tests use an in-memory H2 database and do not require a running MySQL server. The test cases cover external-ID duplicates, normalized-content duplicates, rejected-attempt logging, and password hashing/role assignment.

## Database schema

The three tables are:

- `app_users`: username, BCrypt password hash, role, and creation time.
- `data_records`: accepted data and unique constraints on `external_id` and `fingerprint`.
- `duplicate_attempts`: attempted ID, submitter, match reason, matching record ID, and timestamp. The matching record reference is cleared if that record is later deleted.

Hibernate manages the schema locally by default. `src/main/resources/schema.sql` provides equivalent MySQL DDL for review or manual provisioning. For production, create the database separately, apply the schema with a privileged deployment account, and run the application with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.

## API overview

All API routes are same-origin. Mutating browser requests require the CSRF token returned by `GET /api/csrf` in the `X-XSRF-TOKEN` header. Authentication is a server-side session; the browser does not store a password or bearer token.

| Method | Route | Access | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/csrf` | Public | Initialize/return a CSRF token |
| `POST` | `/api/auth/register` | Public | Create a standard user account |
| `POST` | `/api/auth/login` | Public | Authenticate and create a session |
| `POST` | `/api/auth/logout` | Signed in | End the session |
| `GET` | `/api/auth/me` | Signed in | Return current username and role |
| `POST` | `/api/records` | Signed in | Validate and submit a record |
| `GET` | `/api/records?q=&category=` | Admin | Search and filter records |
| `PUT`, `DELETE` | `/api/records/{id}` | Admin | Update or remove a record |
| `GET` | `/api/dashboard` | Admin | Counts, categories, and recent activity |
| `GET` | `/api/duplicate-attempts` | Admin | Recent duplicate audit log |
| `GET` | `/api/reports/duplicates.csv` | Admin | Download duplicate-attempt report |

## Security and operations notes

- Set an admin at first deployment with `APP_BOOTSTRAP_ADMIN_USERNAME` and `APP_BOOTSTRAP_ADMIN_PASSWORD`. The application only creates it if that username does not already exist. Clear the bootstrap environment values after initial provisioning if your hosting process supports that safely.
- All self-service registrations receive `USER`; there is no public admin-role selector or endpoint.
- Use TLS in front of the application. When HTTPS is enabled, set `SESSION_COOKIE_SECURE=true` (see application properties) and configure trusted proxy forwarding deliberately.
- Store database/admin secrets in a managed secret store or protected environment file, not in Git, container images, or AMIs. Rotate secrets and restrict database permissions.
- Do not expose MySQL/RDS to the internet. Back up the database and test restore procedures.
- The duplicate fingerprint is a deterministic hash for equality checks, not encryption. It does not replace encryption at rest for sensitive records.

## AWS EC2 + RDS deployment guide

This is a manual deployment outline; it does not deploy resources automatically.

1. **Create the network boundary.** Use a VPC with public subnets for an HTTPS entry point and private subnets for RDS. Create separate security groups. Allow inbound SSH to EC2 only from your current trusted IP; allow HTTPS (443) from clients. Do not open the RDS port publicly.
2. **Provision RDS for MySQL.** Choose a supported MySQL version, private DB subnet group, encryption at rest, automated backups, and a strong master secret in AWS Secrets Manager. Set the RDS security group inbound rule to MySQL port 3306 from the application security group only.
3. **Create the application database and least-privilege user.** Connect through a controlled administrative path (for example, Systems Manager or a temporary bastion), create `data_guard`, and grant the app user only the privileges needed by your schema/migration approach. Avoid using the RDS master user in the app.
4. **Create EC2.** Use a supported Amazon Linux image, a small instance appropriate for the demo, an instance profile for Systems Manager/Secrets Manager access, and the application security group. Patch the OS and install a Java 17 runtime. Keep RDS and application hosts in the same VPC/region.
5. **Build and transfer the app.** In a trusted build environment run `mvn clean package`; copy `target/data-guard-1.0.0.jar` to EC2 or publish the Docker image to ECR. Do not copy `.env` or source-control secrets into the image.
6. **Configure runtime environment.** Set `DB_URL` to `jdbc:mysql://<rds-endpoint>:3306/data_guard?sslMode=VERIFY_IDENTITY&serverTimezone=UTC`, `DB_USERNAME`, `DB_PASSWORD`, `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, and `SESSION_COOKIE_SECURE=true`. Retrieve secrets through Secrets Manager or a root-readable environment file. Set the bootstrap admin values only for initial account creation, then remove them and restart.
7. **Set up HTTPS and process management.** Run the JAR under a dedicated non-root systemd service. Put Nginx or an AWS load balancer in front, configure an ACM/Let's Encrypt certificate, and forward HTTPS traffic to the app. Configure health monitoring, logs, alarms, and a backup/restore test before sharing the URL.
8. **Verify the live deployment.** Confirm login, role separation, record creation, duplicate rejection, report export, RDS backups, TLS, and the EC2-to-RDS network path. Only describe it as deployed after completing this verification.

For a class demo, a single EC2 instance and a small RDS instance can be enough, but they are not automatically highly available. Review AWS pricing, free-tier eligibility, and cleanup steps before creating billable resources.

## GitHub upload

1. Create an empty GitHub repository. Do not add a license or README there if you already have this local README, unless you plan to merge them.
2. From this folder, check that `.env` and secrets are not staged, then initialize and push:

   ```powershell
   git init
   git add .
   git status
   git commit -m "Build Cloud Data Guard application"
   git branch -M main
   git remote add origin https://github.com/<your-user>/<your-repository>.git
   git push -u origin main
   ```

3. If prompted for authentication, use GitHub's approved credential manager or SSH setup. Never paste a personal access token into source files or commit history.

## Screenshots checklist

Capture real screenshots after the app is running and populated with demo data. Use fictional names and addresses.

- Sign-in screen with the DataGuard brand and account tabs.
- Admin overview showing total/unique/duplicate metrics and category chart.
- Data management table with a search or category filter applied.
- Add-record form and a successful validation confirmation.
- Duplicate submission response showing that the repeat was blocked.
- Duplicate log with match reason and CSV export action.
- Standard-user submission portal demonstrating it cannot browse admin records.
- Optional AWS console views with account IDs, endpoints, and secrets obscured.

## Internship explanation

**Problem:** Repeated data wastes storage and makes reports less trustworthy. A database's unique ID constraint catches repeated identifiers, but not a repeated record sent under a new ID.

**Solution:** DataGuard validates each submission on the server. It first checks the external ID, then computes a SHA-256 fingerprint of normalized record fields and compares that fingerprint with the database. A match is saved in the audit log as a rejected attempt; otherwise the record is stored. Unique database constraints provide a final safeguard if two requests arrive close together.

**Cloud design:** Spring Boot serves both the web interface and API. In AWS, the application can run on EC2 and connect privately to MySQL on RDS. Security groups isolate the database, HTTPS protects traffic, and environment/secret-manager configuration keeps credentials out of the code.

**What I learned:** REST APIs, backend validation, relational schema design, Spring Data JPA, BCrypt authentication, role-based access, duplicate detection, reporting, Docker-based local setup, and cloud network/security planning.

**Current scope:** Exact normalized comparison, not approximate/fuzzy matching; one application service with a relational database; manual AWS deployment instructions only. No AWS deployment, production load test, or external security audit is included in this repository.
