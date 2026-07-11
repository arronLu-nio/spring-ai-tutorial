package com.example.springaitutorial.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import com.example.springaitutorial.memory.SystemAwareChatMemory;

/**
 * 演示“系统消息固定保留 + 普通消息窗口裁剪”。
 */
@RestController
public class SystemAwareMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public SystemAwareMemoryController(ChatClient.Builder chatClientBuilder,
                                       RedisChatMemoryRepository repository) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        ChatMemory windowMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                // 只限制普通消息，系统消息由 SystemAwareChatMemory 固定补到最前面。
                .maxMessages(4)
                .build();

        ChatMemory systemAwareMemory = new SystemAwareChatMemory(
                windowMemory,
                "你是一名严格的 Java 老师。回答必须使用简单中文，并给出最小 Java 示例。"
        );
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(systemAwareMemory).build();
    }

    @GetMapping(value = "/ai/memory/system-aware", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        return chatClient.prompt()
                // SystemMessage 已由自定义 ChatMemory 自动注入，不在这里重复添加。
                .advisors(advisorSpec -> advisorSpec
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }
}
