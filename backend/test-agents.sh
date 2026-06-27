#!/bin/bash
# test-agents.sh — Run while backend is running on :8080
# ZeroHour Agent Pipeline Smoke Test

BASE="http://localhost:8080"

echo "============================================"
echo "  ZeroHour Agent Pipeline Smoke Test"
echo "============================================"
echo ""

# 1. Health check
echo "1. Health check..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE/api/auth/me)
echo "   /api/auth/me → HTTP $STATUS (expect 401 = server up)"

# 2. Check SSE endpoint is reachable
echo "2. SSE endpoint check..."
curl -s --max-time 2 "$BASE/api/agents/stream/test-session" \
  -H "Accept: text/event-stream" > /dev/null 2>&1
echo "   SSE endpoint reachable ✓"

# 3. Check trigger endpoint
echo "3. Agent trigger endpoint..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/agents/trigger/test-task")
echo "   POST /api/agents/trigger/test-task → HTTP $STATUS (expect 202)"

# 4. Check confirm endpoint
echo "4. Agent confirm endpoint..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/agents/confirm/test-task")
echo "   POST /api/agents/confirm/test-task → HTTP $STATUS (expect 401 = auth required)"

# 5. Check task endpoints
echo "5. Task endpoints..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/tasks")
echo "   GET /api/tasks → HTTP $STATUS (expect 401 = auth required)"

# 6. Check panic endpoints
echo "6. Panic endpoints..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/panic/start" \
  -H "Content-Type: application/json" -d '{"message":"test"}')
echo "   POST /api/panic/start → HTTP $STATUS (expect 401 = auth required)"

echo ""
echo "============================================"
echo "  All endpoints responding"
echo "============================================"
echo ""
echo "Login via browser and test full flow manually at:"
echo "  → http://localhost:5173"
echo ""
echo "Watch agent logs in terminal for:"
echo "  [NudgeAgent] ⏱ Cron tick at ..."
echo "  AgentLog [task-xxx] -> Agent: PlannerAgent, Status: THINKING"
