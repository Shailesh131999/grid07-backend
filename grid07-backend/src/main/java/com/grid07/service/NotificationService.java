package com.grid07.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Handles smart notification batching so users don't get spammed.
 * Logic: if a user got a notification in the last 15 min, queue it.
 * Otherwise send it immediately and start a 15-min cooldown.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RedisTemplate<String, String> redisTemplate;

    public NotificationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void handleBotInteractionNotification(Long userId, String botName, String interactionDescription) {
        String cooldownKey   = "notif_cooldown:user_" + userId;
        String pendingListKey = "user:" + userId + ":pending_notifs";

        String message = "Bot " + botName + " " + interactionDescription;

        boolean onCooldown = Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey));

        if (onCooldown) {
            // user already got a notification recently, queue this one for batch delivery
            redisTemplate.opsForList().rightPush(pendingListKey, message);
            log.info("Notification queued for user {} -> {}", userId, message);
        } else {
            // send immediately and start the cooldown window
            log.info("Push Notification Sent to User {}: {}", userId, message);
            redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofMinutes(15));
        }
    }
}
