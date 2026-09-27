# Render Environment Variable Setup (Backend)

## Problem
The backend on Render needs environment variables configured for different deployment environments (Development, Preprod, Production) to work correctly.

**Current state:**
- ✅ Backend services deployed to Render
- ❌ Environment variables not configured in Render dashboard
- ⚠️ Applications may not start properly or may use wrong database/settings

**Result:** Potential runtime failures, wrong database connections, or misconfigured services.

---

## Solution: Add Environment Variables to Render

### Step 1: Go to Render Dashboard
Open **https://dashboard.render.com** and log in

### Step 2: Select Backend Service
Select one of your backend services:
- `sasbo-online-backend-dev` (Development)
- `sasbo-online-backend-preprod` (Preprod/Preview)
- `sasbo-online-backend-prod` (Production)

![Step 1-2](./docs/render-step-1-dashboard.png)

### Step 3: Go to Environment
In the left sidebar, click **Environment**

![Step 3](./docs/render-step-3-environment.png)

### Step 4: Add Environment Variables

Click **Add Environment Variable** for each variable below.

---

## Environment Variables by Service

### DEVELOPMENT - `sasbo-online-backend-dev`

Used for local/development testing and staging.

| Name | Value | Description |
|------|-------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://[DEV_DB_HOST]:[DB_PORT]/[DEV_DB_NAME]` | Dev database connection |
| `SPRING_DATASOURCE_USERNAME` | (dev database user) | Database username |
| `SPRING_DATASOURCE_PASSWORD` | (dev database password) | Database password |
| `SPRING_PROFILES_ACTIVE` | `development` | Spring profile: development |
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Schema validation |
| `LOGGING_LEVEL_ROOT` | `DEBUG` | Root logging level (verbose) |
| `LOGGING_LEVEL_ZA_CO_TITANDYNAMIX` | `DEBUG` | Application logging (verbose) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,https://*-preprod.vercel.app` | Dev CORS origins |

---

### PREPROD - `sasbo-online-backend-preprod`

Used for staging and testing before production.

| Name | Value | Description |
|------|-------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://[PREPROD_DB_HOST]:[DB_PORT]/[PREPROD_DB_NAME]` | Preprod database connection |
| `SPRING_DATASOURCE_USERNAME` | (preprod database user) | Database username |
| `SPRING_DATASOURCE_PASSWORD` | (preprod database password) | Database password |
| `SPRING_PROFILES_ACTIVE` | `preprod` | Spring profile: preprod |
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Schema validation |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOGGING_LEVEL_ZA_CO_TITANDYNAMIX` | `INFO` | Application logging |
| `CORS_ALLOWED_ORIGINS` | `https://*-preprod.vercel.app,https://preprod.sasbo.online` | Preprod CORS origins |

---

### PRODUCTION - `sasbo-online-backend-prod`

Used for live production environment.

| Name | Value | Description |
|------|-------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://[PROD_DB_HOST]:[DB_PORT]/[PROD_DB_NAME]` | Production database connection |
| `SPRING_DATASOURCE_USERNAME` | (prod database user) | Database username |
| `SPRING_DATASOURCE_PASSWORD` | (prod database password) | Database password |
| `SPRING_PROFILES_ACTIVE` | `production` | Spring profile: production |
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Schema validation (strict) |
| `LOGGING_LEVEL_ROOT` | `WARN` | Root logging level (minimal) |
| `LOGGING_LEVEL_ZA_CO_TITANDYNAMIX` | `INFO` | Application logging |
| `CORS_ALLOWED_ORIGINS` | `https://sasbo.online,https://www.sasbo.online` | Production CORS origins |

---

## Quick Reference Table

| Variable | Development | Preprod | Production |
|----------|-------------|---------|------------|
| **Database URL** | `jdbc:postgresql://[DEV_HOST]/[DEV_DB]` | `jdbc:postgresql://[PREPROD_HOST]/[PREPROD_DB]` | `jdbc:postgresql://[PROD_HOST]/[PROD_DB]` |
| **Database User** | (dev user) | (preprod user) | (prod user) |
| **Database Password** | (dev password) | (preprod password) | (prod password) |
| **Spring Profile** | `development` | `preprod` | `production` |
| **Logging Level** | `DEBUG` | `INFO` | `WARN` |
| **CORS Origins** | `http://localhost:5173,https://*-preprod.vercel.app` | `https://*-preprod.vercel.app,https://preprod.sasbo.online` | `https://sasbo.online,https://www.sasbo.online` |

---

## Step-by-Step in Render Dashboard

### For Each Service (Development, Preprod, Production):

1. Go to **Render Dashboard**
2. Click on the backend service (e.g., `sasbo-online-backend-dev`)
3. Click **Environment** in left sidebar
4. For each variable:
   - Click **Add Environment Variable**
   - Enter **Name** (e.g., `SPRING_DATASOURCE_URL`)
   - Enter **Value** (from table above)
   - Click **Save**

![Example](./docs/render-add-env-var.png)

### Full Lists to Add:

**Development (`sasbo-online-backend-dev`):**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://[DEV_DB_HOST]:[DB_PORT]/[DEV_DB_NAME]
SPRING_DATASOURCE_USERNAME=[dev-user]
SPRING_DATASOURCE_PASSWORD=[dev-password]
SPRING_PROFILES_ACTIVE=development
SERVER_PORT=8080
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
LOGGING_LEVEL_ROOT=DEBUG
LOGGING_LEVEL_ZA_CO_TITANDYNAMIX=DEBUG
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://*-preprod.vercel.app
```

**Preprod (`sasbo-online-backend-preprod`):**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://[PREPROD_DB_HOST]:[DB_PORT]/[PREPROD_DB_NAME]
SPRING_DATASOURCE_USERNAME=[preprod-user]
SPRING_DATASOURCE_PASSWORD=[preprod-password]
SPRING_PROFILES_ACTIVE=preprod
SERVER_PORT=8080
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_ZA_CO_TITANDYNAMIX=INFO
CORS_ALLOWED_ORIGINS=https://*-preprod.vercel.app,https://preprod.sasbo.online
```

**Production (`sasbo-online-backend-prod`):**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://[PROD_DB_HOST]:[DB_PORT]/[PROD_DB_NAME]
SPRING_DATASOURCE_USERNAME=[prod-user]
SPRING_DATASOURCE_PASSWORD=[prod-password]
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_ZA_CO_TITANDYNAMIX=INFO
CORS_ALLOWED_ORIGINS=https://sasbo.online,https://www.sasbo.online
```

---

## After Adding All Variables

### Redeploy Each Service

For each backend service (Development, Preprod, Production):

1. In Render dashboard, go to the service
2. Click **Manual Deploy** (or push to corresponding branch for auto-deploy)
3. Watch the build and deployment logs
4. Verify the service is running

---

## Database Configuration Details

### Finding Your Database Connection Info

For each environment (dev, preprod, prod):

1. Go to **Render Dashboard → Databases**
2. Find the corresponding database (e.g., `sasbo-dev-db`, `sasbo-preprod-db`, `sasbo-prod-db`)
3. Click on it
4. Scroll to **Connections** section
5. Copy the **External Database URL**

This will look like:
```
postgresql://user:[REDACTED]@host:5432/dbname
```

Convert this to the three separate variables:
- **SPRING_DATASOURCE_URL:** `jdbc:postgresql://host:5432/dbname`
- **SPRING_DATASOURCE_USERNAME:** `user`
- **SPRING_DATASOURCE_PASSWORD:** `password`

---

## Verification

### Expected Result
After redeployment completes:
- ✅ Application starts without errors
- ✅ Database migrations run
- ✅ API endpoints respond (HTTP 200)
- ✅ Health check returns UP

### How to Verify by Environment

**Development:**
```bash
curl https://sasbo-online-backend-dev.onrender.com/api/banks
# Should return HTTP 200 with bank list
```

**Preprod:**
```bash
curl https://sasbo-online-backend-preprod.onrender.com/api/banks
# Should return HTTP 200 with bank list
```

**Production:**
```bash
curl https://sasbo-online-backend-prod.onrender.com/api/banks
# Should return HTTP 200 with bank list
```

**Health Checks:**
```bash
# Development
curl https://sasbo-online-backend-dev.onrender.com/actuator/health

# Preprod
curl https://sasbo-online-backend-preprod.onrender.com/actuator/health

# Production
curl https://sasbo-online-backend-prod.onrender.com/actuator/health
```

### Check Application Logs

1. Go to **Render Dashboard → Service**
2. Click **Logs** tab
3. Look for:
   ```
   Application started successfully
   Flyway migration completed
   EntityManagerFactory initialized
   Spring context initialized
   ```

---

## If Deployment Fails

### Check Build Logs
1. Go to **Render Dashboard → Service**
2. Click **Logs** tab
3. Look for error messages

### Common Issues

| Error | Solution |
|-------|----------|
| `Connection refused` | Check `SPRING_DATASOURCE_URL` and database is running |
| `Authentication failed` | Check `SPRING_DATASOURCE_USERNAME` and `PASSWORD` |
| `Unknown database` | Check database name in `SPRING_DATASOURCE_URL` |
| `Port already in use` | Verify `SERVER_PORT` is correct (usually 8080) |
| `Profile not found` | Verify `SPRING_PROFILES_ACTIVE` value matches application config |

### Restart Service
If logs look good but service isn't responding:
1. Go to **Render Dashboard → Service → Settings**
2. Scroll to bottom
3. Click **Restart Service** or **Delete and Redeploy**

---

## Environment Profiles

The backend uses Spring profiles. Set `SPRING_PROFILES_ACTIVE` accordingly:

| Profile | Use Case | Features | Data |
|---------|----------|----------|------|
| `development` | Local/staging | Debug logging, relaxed validation | Test data |
| `preprod` | Pre-production staging | Info logging, standard validation | Production-like data |
| `production` | Live users | Minimal logging, strict validation | Production data |

---

## Security Notes

⚠️ **Never commit environment variables to git:**
- Keep database credentials out of version control
- Use strong, unique passwords for each environment
- Rotate credentials regularly

✅ **Render securely stores environment variables:**
- Encrypted at rest
- Not visible in logs
- Only accessible to service owners
- Audit trail of changes

---

## Reference: Spring Boot Properties

If you need to add more Spring Boot properties:

```properties
# Database
spring.datasource.url=
spring.datasource.username=
spring.datasource.password=

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Logging
logging.level.root=INFO
logging.level.za.co.titandynamix=INFO

# Server
server.port=8080
server.servlet.context-path=/

# CORS
cors.allowed.origins=https://sasbo.online
```

---

## Environment-to-Environment Promotion

Typical deployment flow:

```
Development → Preprod → Production
   ↓            ↓           ↓
Test changes   QA test    Live users
```

1. **Development:** Test new features locally and on dev backend
2. **Preprod:** Run QA and integration tests before production
3. **Production:** Deploy to live users

Each environment has its own:
- Database
- Backend service
- Frontend deployment (Vercel)
- Configuration

---

## Contact

If you encounter issues:
1. Check application logs in Render dashboard
2. Verify database connectivity (Render → Databases)
3. Verify environment variable names (case-sensitive)
4. Verify values match your database credentials
5. Contact backend team for configuration support
