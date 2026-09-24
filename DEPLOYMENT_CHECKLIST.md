# Deployment Checklist — Render Timeout Fix

## Pre-Deployment (Local Testing)

### Build & Verify
- [ ] Run `./gradlew clean build -x test` — must succeed with no errors
- [ ] Check build output ends with `BUILD SUCCESSFUL`
- [ ] JAR size is reasonable (should be ~30-50MB)

### Local Runtime Test
```bash
# Start local backend with production settings
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sasbo_membership
export SPRING_DATASOURCE_USERNAME=clivememela
export SPRING_DATASOURCE_PASSWORD=
export SSL_MODE=prefered
export SPRING_PROFILES_ACTIVE=production

java -jar build/libs/sasbo-online-backend-0.0.1-SNAPSHOT.jar
```

**Verify in logs:**
- [ ] `🔌 HikariCP DataSource configured: max=20, min=3, timeout=10000ms`
- [ ] `Started SasboOnlineBackendApplication`
- [ ] No ERROR or WARN messages in first 10 lines
- [ ] Logging level shows INFO (not DEBUG)

### Test Local Endpoints
```bash
# Health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Banks endpoint
curl http://localhost:8080/api/banks | jq . | head -20
# Expected: Array of bank objects, response time <500ms

# Members endpoint  
curl http://localhost:8080/api/members | jq . | head -20
# Expected: Array of member objects, response time <1s
```

- [ ] /actuator/health returns UP
- [ ] /api/banks returns JSON with banks
- [ ] /api/members returns JSON with members
- [ ] All requests complete in <2 seconds

---

## Deployment to Render

### Step 1: Environment Variables
In Render Dashboard for your backend service:

**Settings → Environment**

Add/Update:
```
SPRING_DATASOURCE_URL=postgresql://[USER]:[REDACTED]@[HOST]:[PORT]/[DB]?sslmode=require
SPRING_DATASOURCE_USERNAME=[USER]
SPRING_DATASOURCE_PASSWORD=[REDACTED]
SSL_MODE=require
SERVER_PORT=8080
FRONTEND_URL=https://your-frontend.vercel.app
SPRING_PROFILES_ACTIVE=production
SASBO_ENVIRONMENT=production
```

- [ ] All 7 environment variables set
- [ ] SSL_MODE=require (not disable)
- [ ] FRONTEND_URL matches your Vercel domain
- [ ] SPRING_PROFILES_ACTIVE=production
- [ ] SASBO_ENVIRONMENT=production

### Step 2: Git Push
```bash
git add -A
git commit -m "fix: production timeout optimization - connection pool, async logging, JVM tuning"
git push origin main
```

- [ ] Git push succeeded
- [ ] No untracked files
- [ ] Commit message is clear

### Step 3: Monitor Deployment
In Render Dashboard:

**Service → Events**

Wait for deployment to complete. Watch logs:

```
✓ Deploy succeeded
✓ Service is live
✓ Health check passed
```

- [ ] Deployment status shows "Live"
- [ ] No red error indicators
- [ ] Latest event is "Deploy succeeded"

### Step 4: Review Startup Logs
In Render Dashboard:

**Service → Logs**

Look for (in order):
1. `Building Docker image` ✓
2. `Launching container` ✓
3. `Spring Boot started` ✓
4. `🔌 HikariCP DataSource configured: max=20` ✓
5. `Started SasboOnlineBackendApplication` ✓

**Should NOT see:**
- ✗ DEBUG log lines (indicates config error)
- ✗ OutOfMemory errors
- ✗ Connection timeout errors
- ✗ SQL Exception

- [ ] All 5 startup messages present
- [ ] No error messages in logs
- [ ] Logs show INFO level (not DEBUG)

---

## Post-Deployment Testing

### Test Backend Directly
```bash
# Replace with your Render URL
BACKEND_URL="https://your-backend.onrender.com"

# Health check
curl $BACKEND_URL/actuator/health
# Expected: {"status":"UP","components":...}

# Banks (should be <500ms)
time curl $BACKEND_URL/api/banks
# Expected: JSON array, ~150-300ms

# Members (should be <1s)
time curl $BACKEND_URL/api/members  
# Expected: JSON array, ~300-600ms
```

- [ ] Health check returns UP
- [ ] Banks endpoint responds <500ms
- [ ] Members endpoint responds <1000ms
- [ ] No 500 errors in responses

### Test Frontend → Backend
On Vercel frontend:

1. Open browser DevTools → Console
2. Navigate to `/` (home page)
3. Wait for page to load

**Check:**
- [ ] No timeout errors in console
- [ ] No red error messages
- [ ] Data loads and displays
- [ ] /api/banks loads first
- [ ] /api/members loads second

**DevTools → Network tab:**
- [ ] All API requests show green (200 status)
- [ ] No requests timeout
- [ ] Response times <2 seconds each
- [ ] No red X on any request

### Monitor Render Metrics

In Render Dashboard:

**Service → Metrics**

- [ ] Memory usage: 50-70% (stable, not climbing)
- [ ] CPU usage: 5-20% idle, <50% under load
- [ ] Network In/Out: consistent, no spikes
- [ ] Request count: normal (not dropping to zero)

---

## Production Monitoring (First 24 Hours)

### Check Logs Regularly
```bash
# Every hour, review logs for:
# - "SLOW REQUEST" (timeout indicators)
# - "ERROR" (failures)
# - "OutOfMemory" (memory leak)
```

- [ ] No SLOW REQUEST logs
- [ ] No repeated ERROR patterns
- [ ] No OutOfMemory errors
- [ ] Response times stable

### Load Test (Optional but Recommended)
```bash
# Using Apache Bench (10 concurrent, 100 requests)
ab -n 100 -c 10 https://your-backend.onrender.com/api/banks

# Expected:
# Requests per second: >100
# Failed requests: 0
# Mean time per request: <50ms
```

- [ ] No failed requests
- [ ] Response times consistent
- [ ] Requests per second acceptable

### User Acceptance Testing
Have a user test in production:

- [ ] Load members page — completes <5 seconds
- [ ] Search for member — completes <2 seconds
- [ ] Create new member — completes <3 seconds
- [ ] Edit member — completes <2 seconds
- [ ] No timeout errors encountered
- [ ] No 500 error responses

---

## Verification Summary

| Item | Status | Notes |
|------|--------|-------|
| Build passes locally | ⚪ | Should succeed |
| Local endpoints respond | ⚪ | <2s response time |
| Render env vars set | ⚪ | All 7 required |
| Deployment succeeded | ⚪ | Live status |
| Logs show startup OK | ⚪ | HikariCP configured |
| Backend responds | ⚪ | Health check UP |
| Frontend loads data | ⚪ | No timeouts |
| 24hr monitoring OK | ⚪ | No issues |

---

## Rollback Plan (If Needed)

If production breaks after deployment:

```bash
# Revert last commit
git revert HEAD
git push origin main

# Render auto-deploys previous version
# Monitor logs to confirm stable
```

Expected time to rollback: ~2-3 minutes

- [ ] Have previous commit SHA ready
- [ ] Know how to use git revert
- [ ] Understand rollback deployment process

---

## Success Criteria

✅ **Fix is successful if:**
1. No "timeout" errors in frontend console
2. All API endpoints respond in <2 seconds
3. Banks/members load within 5 seconds
4. Render metrics show stable memory/CPU
5. No ERROR or SLOW REQUEST logs

---

## Support Contacts

If deployment fails:

1. **Render Support:** https://render.com/support
2. **Check Status:** https://status.render.com/
3. **GitHub Actions:** Check CI/CD logs
4. **Local Reproduction:** Test locally first

---

## Deployment Timestamp

**Date:** [Fill in actual deployment date]  
**Deployed by:** [Name]  
**Version:** [Commit SHA]  
**Status:** ⭕ Pending / ✅ Complete / ❌ Rolled back

---

**Last Updated:** 2024
