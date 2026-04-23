package com.grid07.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Runs every 5 minutes to sweep pending notifications and send summarized batches.
 * In production this would be every 15 min, but 5 min works better for testing.
 */
@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final RedisTemplate<String, String> redisTemplate;

    public SchedulerService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    public void sweepPendingNotifications() {
        log.info("--- CRON SWEEPER STARTED: scanning for pending notifications ---");

        // find all pending notif queues across all users
        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");

        if (keys == null || keys.isEmpty()) {
            log.info("--- CRON SWEEPER: no pending notifications found. ---");
            return;
        }

        for (String listKey : keys) {
            processPendingForUser(listKey);
        }

        log.info("--- CRON SWEEPER DONE ---");
    }

    private void processPendingForUser(String listKey) {
        // grab everything in the list, then delete it atomically
        List<String> messages = redisTemplate.opsForList().range(listKey, 0, -1);

        if (messages == null || messages.isEmpty()) {
            return;
        }

        // delete the list right away before processing - prevents double-send if scheduler runs again
        redisTemplate.delete(listKey);

        // extract user id from key pattern "user:{id}:pending_notifs"
        String userId = listKey.split(":")[1];

        if (messages.size() == 1) {
            log.info("Summarized Push Notification to User {}: {}", userId, messages.get(0));
        } else {
            // show the first bot name + how many others
            String firstMessage = messages.get(0);
            int othersCount = messages.size() - 1;
            log.info("Summarized Push Notification to User {}: {} and [{}] others interacted with your posts.",
                    userId, firstMessage, othersCount);
        }
    }
}
