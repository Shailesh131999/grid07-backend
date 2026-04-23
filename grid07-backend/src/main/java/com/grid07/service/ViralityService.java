package com.grid07.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViralityService {

    private static final Logger log = LoggerFactory.getLogger(ViralityService.class);

    private static final int BOT_REPLY_POINTS   = 1;
    private static final int HUMAN_LIKE_POINTS  = 20;
    private static final int HUMAN_COMMENT_POINTS = 50;

    private final RedisTemplate<String, String> redisTemplate;

    public ViralityService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordBotReply(Long postId) {
        String key = viralityKey(postId);
        Long newScore = redisTemplate.opsForValue().increment(key, BOT_REPLY_POINTS);
        log.info("Bot replied on post {} -> virality score now: {}", postId, newScore);
    }

    public void recordHumanLike(Long postId) {
        String key = viralityKey(postId);
        Long newScore = redisTemplate.opsForValue().increment(key, HUMAN_LIKE_POINTS);
        log.info("Human liked post {} -> virality score now: {}", postId, newScore);
    }

    public void recordHumanComment(Long postId) {
        String key = viralityKey(postId);
        Long newScore = redisTemplate.opsForValue().increment(key, HUMAN_COMMENT_POINTS);
        log.info("Human commented on post {} -> virality score now: {}", postId, newScore);
    }

    public Long getViralityScore(Long postId) {
        String val = redisTemplate.opsForValue().get(viralityKey(postId));
        return val != null ? Long.parseLong(val) : 0L;
    }

    private String viralityKey(Long postId) {
        return "post:" + postId + ":virality_score";
    }
}
