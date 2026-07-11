package com.example.springaitutorial.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class MemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public MemoryController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        // Spring AI 默认提供内存版 ChatMemory，适合学习和本地 Demo。
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @GetMapping(value = "/ai/memory/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        return chatClient.prompt()
                // system message：定义整个会话的长期角色和回答规则
                .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
                .advisors(advisorSpec -> advisorSpec
                        // 把记忆 Advisor 加入本次请求
                        .advisors(memoryAdvisor)
                        // conversationId 决定读取和保存哪一段对话
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }
}
