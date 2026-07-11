package com.example.springaitutorial.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 演示 SystemMessage 和 ChatMemory 的分工。
 */
@RestController
public class SystemMessageMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public SystemMessageMemoryController(ChatClient.Builder chatClientBuilder,
                                         ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @GetMapping(value = "/ai/memory/system-message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        return chatClient.prompt()
                // SystemMessage 每次请求重新加入，定义模型本轮必须遵守的规则。
                .system("你是一名严格的 Java 老师。回答必须使用简单中文，并且每次都给出一个最小 Java 示例。")
                .advisors(advisorSpec -> advisorSpec
                        // Memory Advisor 负责读取历史 User/Assistant 消息并保存本轮消息。
                        .advisors(memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                .stream()
                .content();
    }
}
