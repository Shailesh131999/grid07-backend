# Grid07 Backend Assignment

Spring Boot microservice with Redis-backed guardrails and event-driven scheduling.

---

## Setup & Running

**Step 1: Start Postgres + Redis**
```bash
docker-compose up -d
```

**Step 2: Run the Spring Boot app**
```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`

---

## Project Structure

```
src/main/java/com/grid07/
├── config/          RedisConfig.java
├── controller/      PostController.java
├── dto/             Request/Response classes
├── entity/          User, Bot, Post, Comment
├── exception/       GuardrailException, GlobalExceptionHandler
├── repository/      JPA repositories
└── service/
    ├── PostService.java
    ├── CommentService.java
    ├── ViralityService.java
    ├── GuardrailService.java
    ├── NotificationService.java
    └── SchedulerService.java
```

---

## How Thread Safety Works (Phase 2 Answer)

This is the part I want to explain clearly because it's the core of the assignment.

### The Horizontal Cap (100 bot replies per post)

The naive approach would be: read the count from Redis, check if < 100, then write. This is wrong under concurrent load - two threads could both read 99, both think they're allowed, and you'd end up at 101.

**What I did instead:**

Redis `INCR` is an atomic single-step operation. It increments and returns the new value in one shot - no other thread can interleave. So the logic is:

```
count = INCR post:{id}:bot_count   ← atomic
if count > 100:
    DECR post:{id}:bot_count       ← roll back
    return 429
```

Thread 100 gets count=100 → allowed.
Thread 101 gets count=101 → rejected, rolled back to 100.
Thread 102 gets count=101 (after 101 rolled back) → actually this is still 101, also rejected.

The key insight: once we're at 100, every subsequent INCR will land at 101+, get rolled back, and be rejected. The counter never stays above 100. You can fire 10,000 concurrent requests and exactly 100 will make it through.

### The Cooldown Cap (bot-to-human, 10 minute window)

Redis `SET key value NX EX ttl` is a single atomic command (not two separate SET + EXPIRE calls). NX means "only set if the key doesn't exist". So:

```
result = SET cooldown:bot_1:human_1 "1" NX EX 600
if result == null:   ← key already existed, someone beat us to it
    return 429
```

Two concurrent bot requests targeting the same user: one gets `OK`, the other gets `null`. Exactly one gets through. After 10 minutes the key auto-expires and the cooldown lifts.

### Why no in-memory state

The application stores zero counters in Java memory (no HashMaps, no static fields). Everything lives in Redis. This means:
- You can run multiple app instances behind a load balancer and the guardrails still work
- Restarting the app doesn't reset any counts
- Redis is the single source of truth for all rate limiting

### PostgreSQL as source of truth

Redis gatekeeps, Postgres stores. The flow for a bot comment is:

1. Check Redis guardrails (horizontal cap, vertical cap, cooldown)
2. If all pass → save to Postgres
3. If Postgres save fails → roll back the Redis bot counter

This keeps them in sync even if the DB throws an exception mid-request.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/posts` | Create a post |
| POST | `/api/posts/{id}/comments` | Add a comment (guardrails apply for bots) |
| POST | `/api/posts/{id}/like` | Like a post |
| GET  | `/api/posts/{id}/virality` | Get current virality score |

Import `Grid07_Postman_Collection.json` into Postman to test all endpoints.

---

## Virality Points

| Action | Points |
|--------|--------|
| Bot reply | +1 |
| Human like | +20 |
| Human comment | +50 |

---

## Notification Logic

- Bot interacts with a user's post → check if `notif_cooldown:user_{id}` key exists in Redis
- If **no cooldown**: log "Push Notification Sent to User X" + set 15-min cooldown key
- If **on cooldown**: push message to `user:{id}:pending_notifs` Redis list

A `@Scheduled` job runs every 5 minutes, pops all queued messages per user, and logs a summarized notification: `"Bot X and [N] others interacted with your posts."`

---

## Testing the Race Condition (200 concurrent requests)

You can test the horizontal cap with Apache Bench or a simple shell loop:

```bash
# fire 200 concurrent POST requests to add bot comments
ab -n 200 -c 200 -p bot_comment.json -T application/json \
   http://localhost:8080/api/posts/1/comments
```

After this, check the bot_count in Redis:
```bash
redis-cli GET post:1:bot_count
# should show exactly 100
```

And query your DB:
```sql
SELECT COUNT(*) FROM comments WHERE post_id = 1 AND author_type = 'BOT';
-- should also show exactly 100
```
