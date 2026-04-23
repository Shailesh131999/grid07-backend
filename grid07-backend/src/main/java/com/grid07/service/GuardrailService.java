package com.grid07.service;

import com.grid07.exception.GuardrailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * All the Redis-based guardrails live here.
 * The key design decision: use atomic Redis operations (INCR, SET NX)
 * so concurrent requests can't slip past each other.
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);

    private static final int BOT_REPLY_HARD_CAP = 100;
    private static final int MAX_THREAD_DEPTH   = 20;

    private final RedisTemplate<String, String> redisTemplate;

    public GuardrailService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tries to claim one slot in the bot-reply counter for this post.
     * Uses INCR which is atomic in Redis - no two threads can get the same value.
     * If we're over the cap, we roll back the increment and reject.
     */
    public void checkAndIncrementBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";
        Long count = redisTemplate.opsForValue().increment(key);

        if (count > BOT_REPLY_HARD_CAP) {
            // roll it back so the counter stays accurate
            redisTemplate.opsForValue().decrement(key);
            log.warn("Bot reply cap hit for post {} (current count: {})", postId, count);
            throw new GuardrailException(
                "This post has reached the maximum bot reply limit (100). No more bot replies allowed.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        log.debug("Bot count for post {} is now {}", postId, count);
    }

    /**
     * Decrements the bot counter - called if the DB save fails after we already incremented.
     * Keeps Redis and DB in sync.
     */
    public void rollbackBotCount(Long postId) {
        String key = "post:" + postId + ":bot_count";
        redisTemplate.opsForValue().decrement(key);
        log.debug("Rolled back bot count for post {}", postId);
    }

    /**
     * Check if a comment thread has gone too deep.
     */
    public void checkDepthLimit(int depthLevel) {
        if (depthLevel > MAX_THREAD_DEPTH) {
            throw new GuardrailException(
                "Comment thread is too deep. Max allowed depth is 20 levels.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }
    }

    /**
     * Bot-to-human cooldown: a specific bot can only interact with a specific human once per 10 minutes.
     * SET NX + EX is a single atomic Redis command - safe for concurrent requests.
     *
     * Returns true if interaction is allowed, throws GuardrailException if blocked.
     */
    public void checkBotCooldown(Long botId, Long humanUserId) {
        String cooldownKey = "cooldown:bot_" + botId + ":human_" + humanUserId;

        // setIfAbsent = SET key value NX EX ttl - atomic, returns false if key already exists
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            cooldownKey, "1", Duration.ofMinutes(10)
        );

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Cooldown active - bot {} already interacted with user {} recently", botId, humanUserId);
            throw new GuardrailException(
                "Bot " + botId + " is on cooldown for this user. Try again in 10 minutes.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        log.debug("Cooldown set: bot {} -> user {} (10 min)", botId, humanUserId);
    }

    public long getCurrentBotCount(Long postId) {
        String val = redisTemplate.opsForValue().get("post:" + postId + ":bot_count");
        return val != null ? Long.parseLong(val) : 0L;
    }
}
