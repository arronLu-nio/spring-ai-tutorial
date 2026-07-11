package com.example.springaitutorial.session;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.RedisClient;

/**
 * 会话目录接口：只使用 Redis，不使用数据库。
 */
@RestController
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(RedisClient redisClient, ChatMemory chatMemory) {
        this.conversationService = new ConversationService(redisClient, chatMemory);
    }

    @PostMapping("/api/conversations")
    public ConversationService.ConversationInfo create(
            @RequestParam(required = false) String title) {
        return conversationService.create(title);
    }

    @GetMapping("/api/conversations")
    public List<ConversationService.ConversationInfo> list() {
        return conversationService.list();
    }

    @DeleteMapping("/api/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
    }

    @DeleteMapping("/api/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMessages(@PathVariable String conversationId) {
        conversationService.clearMessages(conversationId);
    }
}
