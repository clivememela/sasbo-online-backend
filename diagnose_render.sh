#!/bin/bash
# 🔴 RENDER TIMEOUT DIAGNOSTIC TOOL
# Usage: ./diagnose_render.sh https://your-backend.onrender.com

set -e

BACKEND_URL="${1:-http://localhost:8080}"
FRONTEND_URL="${2:-https://localhost:5173}"

echo "🔍 DIAGNOSING RENDER BACKEND"
echo "================================"
echo "Backend:  $BACKEND_URL"
echo "Frontend: $FRONTEND_URL"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASSED=0
FAILED=0

# Test function
test_endpoint() {
    local name=$1
    local method=$2
    local endpoint=$3
    local timeout=$4
    
    echo -n "Testing $name... "
    
    local start=$(date +%s%N)
    local response=$(curl -s -w "\n%{http_code}" -X "$method" \
        -m "$timeout" \
        "$BACKEND_URL$endpoint" 2>&1 || echo "TIMEOUT\n000")
    local end=$(date +%s%N)
    
    local http_code=$(echo "$response" | tail -n1)
    local duration=$((($end - $start) / 1000000))  # Convert to ms
    
    if [ "$http_code" = "000" ] || [ "$http_code" = "TIMEOUT" ]; then
        echo -e "${RED}TIMEOUT (>$timeout seconds)${NC}"
        FAILED=$((FAILED + 1))
        return 1
    elif [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
        echo -e "${GREEN}OK${NC} (${duration}ms, HTTP $http_code)"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo -e "${YELLOW}WARNING${NC} (HTTP $http_code)"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

# Test function with JSON validation
test_json_endpoint() {
    local name=$1
    local endpoint=$2
    local timeout=$3
    
    echo -n "Testing $name JSON... "
    
    local start=$(date +%s%N)
    local response=$(curl -s -w "\n%{http_code}" -X GET \
        -m "$timeout" \
        "$BACKEND_URL$endpoint" 2>&1 || echo "TIMEOUT\n000")
    local end=$(date +%s%N)
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -1)
    local duration=$((($end - $start) / 1000000))
    
    if [ "$http_code" = "000" ] || [ "$http_code" = "TIMEOUT" ]; then
        echo -e "${RED}TIMEOUT${NC}"
        FAILED=$((FAILED + 1))
        return 1
    fi
    
    # Try to parse JSON
    if echo "$body" | jq . > /dev/null 2>&1; then
        local count=$(echo "$body" | jq 'length // 0')
        echo -e "${GREEN}OK${NC} (${duration}ms, $count items, HTTP $http_code)"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo -e "${RED}INVALID JSON${NC} (HTTP $http_code)"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

# 1. Health check
echo "📋 HEALTH CHECKS"
echo "=================="
test_endpoint "Health" "GET" "/actuator/health" "5"

# 2. Core endpoints
echo ""
echo "🔌 CORE ENDPOINTS"
echo "=================="
test_json_endpoint "Banks" "/api/banks" "5"
test_json_endpoint "Members" "/api/members?limit=10" "10"
test_json_endpoint "Job Grades" "/api/job-grades" "5"

# 3. Reference data
echo ""
echo "📚 REFERENCE DATA"
echo "=================="
test_json_endpoint "Provinces" "/api/provinces" "5"
test_json_endpoint "Areas" "/api/areas" "5"

# 4. Connection pool stress test (3 concurrent requests)
echo ""
echo "⚡ STRESS TEST (3 concurrent)"
echo "============================="
(test_endpoint "Concurrent 1" "GET" "/api/banks" "10" &)
(test_endpoint "Concurrent 2" "GET" "/api/members" "10" &)
(test_endpoint "Concurrent 3" "GET" "/api/provinces" "10" &)
wait

# 5. CORS check
echo ""
echo "🔐 CORS CHECK"
echo "=============="
echo -n "Testing CORS header... "
cors_header=$(curl -s -I -H "Origin: $FRONTEND_URL" \
    "$BACKEND_URL/api/banks" | grep -i "Access-Control-Allow-Origin" || echo "")
if [ -n "$cors_header" ]; then
    echo -e "${GREEN}OK${NC}"
    echo "  $cors_header"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}WARNING - No CORS header${NC}"
    FAILED=$((FAILED + 1))
fi

# 6. Database connectivity (through API)
echo ""
echo "🗄️  DATABASE CHECK"
echo "=================="
echo -n "Testing database access... "
db_response=$(curl -s -X GET "$BACKEND_URL/api/members?limit=1")
if echo "$db_response" | jq . > /dev/null 2>&1; then
    echo -e "${GREEN}OK${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${RED}FAILED${NC} - Cannot query database"
    FAILED=$((FAILED + 1))
fi

# 7. Error handling
echo ""
echo "❌ ERROR HANDLING"
echo "================="
echo -n "Testing 404 error handling... "
response=$(curl -s -w "\n%{http_code}" "$BACKEND_URL/api/nonexistent" | tail -n1)
if [ "$response" = "404" ]; then
    echo -e "${GREEN}OK${NC} (HTTP 404)"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}WARNING${NC} (HTTP $response)"
fi

# Summary
echo ""
echo "📊 TEST SUMMARY"
echo "================"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ ALL TESTS PASSED - Backend is healthy!${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}❌ SOME TESTS FAILED - Backend has issues${NC}"
    echo ""
    echo "Recommendations:"
    echo "1. Check Render logs for errors"
    echo "2. Verify database connection"
    echo "3. Check environment variables"
    echo "4. Review application configuration"
    exit 1
fi
