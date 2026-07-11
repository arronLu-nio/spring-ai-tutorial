package com.example.springaitutorial.session;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.Assert;
import redis.clients.jedis.RedisClient;

/**
 * 使用 Redis 保存会话目录，不使用数据库。
 */
public class ConversationService {

    private static final String CONVERSATION_SET = "spring-ai-memory:conversation-ids";
    private static final String CONVERSATION_KEY_PREFIX = "spring-ai-memory:conversation:";
    private static final long TTL_SECONDS = 24 * 60 * 60;

    private final RedisClient redisClient;
    private final ChatMemory chatMemory;

    public ConversationService(RedisClient redisClient, ChatMemory chatMemory) {
        this.redisClient = redisClient;
        this.chatMemory = chatMemory;
    }

    public ConversationInfo create(String title) {
        String conversationId = UUID.randomUUID().toString();
        String now = String.valueOf(Instant.now().toEpochMilli());
        String safeTitle = title == null || title.isBlank() ? "新会话" : title.trim();

        redisClient.sadd(CONVERSATION_SET, conversationId);
        redisClient.hset(key(conversationId), Map.of(
                "id", conversationId,
                "title", safeTitle,
                "createdAt", now,
                "updatedAt", now
        ));
        redisClient.expire(key(conversationId), TTL_SECONDS);
        return new ConversationInfo(conversationId, safeTitle, now, now);
    }

    public List<ConversationInfo> list() {
        return redisClient.smembers(CONVERSATION_SET).stream()
                .map(this::read)
                .filter(info -> info != null)
                .sorted(Comparator.comparing(ConversationInfo::updatedAt).reversed())
                .toList();
    }

    public void touch(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be empty");
        String conversationKey = key(conversationId);
        if (!redisClient.exists(conversationKey)) {
            redisClient.sadd(CONVERSATION_SET, conversationId);
            redisClient.hset(conversationKey, Map.of(
                    "id", conversationId,
                    "title", "新会话",
                    "createdAt", String.valueOf(Instant.now().toEpochMilli())
            ));
        }
        redisClient.hset(conversationKey, "updatedAt", String.valueOf(Instant.now().toEpochMilli()));
        redisClient.expire(conversationKey, TTL_SECONDS);
    }

    public void delete(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be empty");
        chatMemory.clear(conversationId);
        redisClient.srem(CONVERSATION_SET, conversationId);
        redisClient.del(key(conversationId));
    }

    public void clearMessages(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be empty");
        // 只清空 ChatMemory，Redis 中的会话目录仍然保留。
        chatMemory.clear(conversationId);
        touch(conversationId);
    }

    private ConversationInfo read(String conversationId) {
        Map<String, String> values = redisClient.hgetAll(key(conversationId));
        if (values.isEmpty()) {
            // Set 里可能残留已经过期的 ID，读取时顺便清理。
            redisClient.srem(CONVERSATION_SET, conversationId);
            return null;
        }
        return new ConversationInfo(
                values.get("id"),
                values.getOrDefault("title", "新会话"),
                values.getOrDefault("createdAt", ""),
                values.getOrDefault("updatedAt", "")
        );
    }

    private String key(String conversationId) {
        return CONVERSATION_KEY_PREFIX + conversationId;
    }

    public record ConversationInfo(String id, String title, String createdAt, String updatedAt) {
    }
}
