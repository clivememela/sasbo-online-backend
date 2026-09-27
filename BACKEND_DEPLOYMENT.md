# Backend Deployment & Environment Setup

## Overview

This directory contains deployment and configuration guides for the SASBO Online Backend across three environments:
- **Development** - Local testing and staging
- **Preprod** - Pre-production staging for QA
- **Production** - Live production environment

---

## Quick Links

- **[Render Environment Setup](./RENDER_ENV_SETUP.md)** - Configure environment variables in Render dashboard for all 3 backends
- **[Backend Dockerfile](./Dockerfile)** - Docker configuration for containerized deployment
- **[Database Migrations](./READY_TO_DEPLOY.md)** - Schema and data setup
- **[Deployment Checklist](./DEPLOYMENT_CHECKLIST.md)** - Pre-deployment verification steps
- **[Troubleshooting Guide](./RENDER_ENV_SETUP.md#if-deployment-fails)** - Common issues and solutions

---

## Deployment Services

### Development (`sasbo-online-backend-dev`)
- **URL:** `https://sasbo-online-backend-dev.onrender.com`
- **Database:** Development PostgreSQL (separate instance)
- **Logging:** DEBUG level
- **Data:** Test data for development
- **Access:** Team members only

### Preprod (`sasbo-online-backend-preprod`)
- **URL:** `https://sasbo-online-backend-preprod.onrender.com`
- **Database:** Preprod PostgreSQL (separate instance)
- **Logging:** INFO level
- **Data:** Production-like test data
- **Access:** QA, testing, stakeholders

### Production (`sasbo-online-backend-prod`)
- **URL:** `https://sasbo-online-backend-prod.onrender.com`
- **Database:** Production PostgreSQL (protected)
- **Logging:** WARN level
- **Data:** Live production data
- **Access:** End users

---

## Getting Started

### 1. Initial Setup
```bash
# Clone the repository
git clone https://github.com/your-org/sasbo-online-backend.git
cd sasbo-online-backend

# Install dependencies
./gradlew build

# Run tests
./gradlew test
```

### 2. Configure Environment Variables
Follow [RENDER_ENV_SETUP.md](./RENDER_ENV_SETUP.md) to add environment variables to each Render service.

### 3. Deploy
```bash
# Verify tests pass
./gradlew verify

# Push to corresponding branch (Render auto-deploys)
git push origin main              # → Production
git push origin preprod           # → Preprod
git push origin develop           # → Development
```

---

## Environment Variables

Each environment requires specific configuration. See [RENDER_ENV_SETUP.md](./RENDER_ENV_SETUP.md) for:
- Database connection details
- Spring profiles
- Logging levels
- CORS configuration
- API keys and secrets

### Example: Development
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sasbo_dev
SPRING_DATASOURCE_USERNAME=dev_user
SPRING_DATASOURCE_PASSWORD=dev_password
SPRING_PROFILES_ACTIVE=development
LOGGING_LEVEL_ROOT=DEBUG
```

---

## Deployment Workflow

### Development Branch → Dev Environment
```
Feature branch → Develop branch → Dev backend (auto-deploy)
                                         ↓
                                   Test changes
```

### Preprod Branch → Preprod Environment
```
Develop branch → Preprod branch → Preprod backend (auto-deploy)
                                         ↓
                                   QA testing
```

### Main Branch → Production Environment
```
Preprod branch → Main branch → Prod backend (auto-deploy)
                                     ↓
                              Live users
```

---

## CI/CD Pipeline

### Automated on Every Push

1. **Tests Run** - `./gradlew test` on all branches
2. **Build** - `./gradlew build` creates JAR
3. **Security Scan** - Dependency and code security checks
4. **Deploy** - Automatically deployed to corresponding Render service

### Manual Verification

Before pushing to main (production):
```bash
./gradlew verify    # Runs all checks
```

---

## Database Migrations

Flyway handles automatic database migrations on startup. Schema is versioned and applies incrementally.

**Key files:**
- `src/main/resources/db/migration/` - SQL migration files
- Migrations are numbered: `V1__initial.sql`, `V2__add_features.sql`, etc.

**To add a new migration:**
```bash
# Create new file
touch src/main/resources/db/migration/V[N]__[description].sql

# Add your SQL
# Commit and push - automatically runs on deployment
```

---

## Monitoring

### Health Checks

Each environment exposes a health endpoint:

```bash
# Development
curl https://sasbo-online-backend-dev.onrender.com/actuator/health

# Preprod
curl https://sasbo-online-backend-preprod.onrender.com/actuator/health

# Production
curl https://sasbo-online-backend-prod.onrender.com/actuator/health
```

### Logs

View logs in Render dashboard:
1. Go to **Render Dashboard**
2. Select backend service
3. Click **Logs** tab
4. Monitor for errors, warnings, startup messages

### Metrics

Access metrics endpoint:
```bash
# Example for Development
curl https://sasbo-online-backend-dev.onrender.com/actuator/metrics
```

---

## Rollback

If deployment goes wrong:

### Option 1: Automatic Rollback
```bash
git revert [commit-hash]
git push origin main
# Render auto-deploys previous version
```

### Option 2: Manual Rollback in Render
1. Go to **Render Dashboard → Service → Deployments**
2. Find previous working deployment
3. Click **Redeploy**

---

## Troubleshooting

See [RENDER_ENV_SETUP.md - If Deployment Fails](./RENDER_ENV_SETUP.md#if-deployment-fails) for:
- Common errors and solutions
- Database connectivity issues
- Configuration problems
- Logging and debugging

---

## Support

- **Backend Issues:** Check logs in Render dashboard
- **Database Issues:** Verify connection in Render → Databases
- **Configuration Issues:** Review [RENDER_ENV_SETUP.md](./RENDER_ENV_SETUP.md)
- **Deployment Issues:** Check build logs in Render
- **Team Help:** Contact backend team lead

---

## Security Checklist

✅ All secrets in Render environment variables (not in code)  
✅ Database passwords are strong and unique per environment  
✅ CORS properly configured for allowed origins  
✅ API endpoints protected with authentication where needed  
✅ Sensitive logs excluded from production logging  
✅ SSL/TLS enabled for all HTTPS connections  

---

## Related Documentation

- [Frontend Setup Guide](../sasbo-online/FRONTEND_DEPLOYMENT.md)
- [Architecture Overview](./ARCHITECTURE.md)
- [API Documentation](./API.md)
- [Database Schema](./SCHEMA.md)
