# Vercel Preview Environment Deployment

## Prerequisites
- Vercel account with access to `sasbo-online` project
- Backend deployed to Render: `https://sasbo-online-backend-latest.onrender.com`

## Step 1: Set Environment Variable

### Via Vercel Dashboard
1. Go to **https://vercel.com/dashboard**
2. Click on **sasbo-online** project
3. Navigate to **Settings → Environment Variables**
4. Click **Add New**
5. Fill in:
   - **Name:** `VITE_API_BASE_URL`
   - **Value:** `https://sasbo-online-backend-latest.onrender.com`
   - **Environments:** Select **Preview** (or Preview, Production, Development)
6. Click **Save**

### Via Vercel CLI (Alternative)
```bash
cd sasbo-online
vercel env add VITE_API_BASE_URL
# Follow prompts:
# Enter value: https://sasbo-online-backend-latest.onrender.com
# Select environments: Preview
```

## Step 2: Redeploy Preview

### Option A: Via Dashboard
1. Go to **Deployments** tab
2. Find the latest commit
3. Click the three dots menu → **Redeploy**
4. Click **Redeploy** to confirm

### Option B: Via CLI
```bash
cd sasbo-online
vercel deploy --prod    # For production
vercel deploy           # For preview (uses latest branch)
```

### Option C: Push to Git (Auto-Deploy)
```bash
git push origin preview
# Vercel auto-deploys the preview branch
```

## Step 3: Verify Deployment

**Check preview URL:**
1. Go to Vercel Deployments
2. Copy the Preview URL (usually `https://sasbo-online-<hash>.vercel.app`)
3. Open it in browser
4. Open DevTools → Console
5. Check for errors:
   - ✅ No timeout errors
   - ✅ Data loads from banks/members endpoints
   - ✅ No 403 CORS errors

**Test API calls:**
```bash
# Replace with your preview URL
curl https://sasbo-online-<hash>.vercel.app/api/banks
```

## Environment Variables Reference

| Variable | Value | Applies To |
|----------|-------|-----------|
| `VITE_API_BASE_URL` | `https://sasbo-online-backend-latest.onrender.com` | All |
| `VITE_DEBUG` | `true` (optional) | Preview |

## Troubleshooting

### Still timing out?
1. Check that `VITE_API_BASE_URL` is set correctly
2. Verify Render backend is running: `curl https://sasbo-online-backend-latest.onrender.com/api/banks`
3. Check browser Network tab to see actual API URL being called
4. Clear browser cache: DevTools → Application → Clear Storage

### CORS errors (403)?
- Backend CORS allows `*.vercel.app` ✅
- If still blocked, check SecurityConfig.java CORS patterns

### Build fails?
- Check Vercel Build Logs → Deployments → click failed build
- Ensure all dependencies are in `package.json`

## Expected Result

✅ **Preview deployment successful when:**
- Frontend loads without errors
- No timeout messages in console
- API calls return data (banks, members, etc.)
- Response times < 2 seconds

---

**Backend Status Check:**
```bash
curl https://sasbo-online-backend-latest.onrender.com/api/banks
# Expected: HTTP 200 with bank list (0.4s response time)
```

**Frontend Health Check:**
```bash
curl https://your-preview-url.vercel.app/
# Expected: HTTP 200 with HTML page
```
