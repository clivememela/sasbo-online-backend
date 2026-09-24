#!/bin/bash
# QUICK BUILD & DEPLOY GUIDE

echo "═══════════════════════════════════════════════════════════════"
echo "  RENDER TIMEOUT FIX — BUILD & DEPLOY"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Step 1: Clean Build
echo "✓ STEP 1: Building locally..."
./gradlew clean build -x test

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Check error above."
    exit 1
fi

echo "✅ Build successful"
echo ""

# Step 2: Verify JAR
echo "✓ STEP 2: Verifying JAR..."
JAR_PATH="build/libs/sasbo-online-backend-0.0.1-SNAPSHOT.jar"
if [ -f "$JAR_PATH" ]; then
    JAR_SIZE=$(du -sh "$JAR_PATH" | cut -f1)
    echo "✅ JAR found: $JAR_SIZE"
else
    echo "❌ JAR not found at $JAR_PATH"
    exit 1
fi

echo ""

# Step 3: Test locally
echo "✓ STEP 3: Testing locally (60 second timeout)..."
timeout 60 java -Dspring.profiles.active=production \
    -jar "$JAR_PATH" &
SERVER_PID=$!

# Wait for server to start
echo "  Waiting for server to start..."
sleep 10

# Test health
HEALTH=$(curl -s http://localhost:8080/actuator/health | grep -o "UP")
if [ "$HEALTH" = "UP" ]; then
    echo "✅ Health check passed"
else
    echo "❌ Health check failed"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi

# Test banks endpoint
BANKS=$(curl -s http://localhost:8080/api/banks | grep -c "bank")
if [ "$BANKS" -gt 0 ]; then
    echo "✅ Banks endpoint working"
else
    echo "❌ Banks endpoint failed"
fi

# Stop server
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

echo ""

# Step 4: Git push
echo "✓ STEP 4: Pushing to git..."
git add -A
git commit -m "fix: production timeout optimization - connection pool, async logging, JVM tuning" || echo "ℹ️  No changes to commit"
git push origin main

if [ $? -ne 0 ]; then
    echo "❌ Git push failed"
    exit 1
fi

echo "✅ Pushed to main branch"
echo ""

# Final message
echo "═══════════════════════════════════════════════════════════════"
echo "✅ BUILD & PUSH COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "Next steps:"
echo "  1. Monitor Render dashboard for deployment"
echo "  2. Wait for 'Deploy succeeded' message"
echo "  3. Run diagnostic: ./diagnose_render.sh <backend_url>"
echo "  4. Test frontend on Vercel"
echo ""
echo "For detailed deployment guide, see: DEPLOYMENT_CHECKLIST.md"
echo ""
