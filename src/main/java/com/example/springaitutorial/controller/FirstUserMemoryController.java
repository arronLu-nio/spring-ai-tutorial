package com.example.springaitutorial.controller;

import com.example.springaitutorial.memory.FirstUserPlusRecentChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 演示“第一条用户消息 + 最近 4 条消息”的自定义记忆策略。
 */
@RestController
public class FirstUserMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public FirstUserMemoryController(ChatClient.Builder chatClientBuilder,
                                     RedisChatMemoryRepository repository) {
        this.chatClient = chatClientBuilder.build();

        // 底层窗口设置得足够大，真正的裁剪由自定义 ChatMemory 完成。
        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(100)
                .build();

        ChatMemory customMemory = new FirstUserPlusRecentChatMemory(delegate, 4);
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(customMemory).build();
    }

    @GetMapping(value = "/ai/memory/first-user", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        return chatClient.prompt()
                .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
                .advisors(advisorSpec -> advisorSpec
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }
}
