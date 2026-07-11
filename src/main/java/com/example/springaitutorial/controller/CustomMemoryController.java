package com.example.springaitutorial.controller;

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
 * 自定义消息窗口示例：本 Controller 只保留最近 4 条消息。
 */
@RestController
public class CustomMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public CustomMemoryController(ChatClient.Builder chatClientBuilder,
                                  RedisChatMemoryRepository repository) {
        this.chatClient = chatClientBuilder.build();

        ChatMemory customChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                // 4 条消息约等于最近 2 轮 User + Assistant 对话。
                .maxMessages(4)
                .build();

        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(customChatMemory).build();
    }

    @GetMapping(value = "/ai/memory/custom-window", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String conversationId,
                             @RequestParam String message) {
        return chatClient.prompt()
                .system("你是一名耐心的 Java 和 Spring AI 老师，请使用简单的中文回答。")
                .advisors(advisorSpec -> advisorSpec
                        // 使用本 Controller 自己创建的 4 条消息窗口。
                        .advisors(memoryAdvisor)
                        // conversationId 决定读取和保存哪一段历史。
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(message)
                // stream：流式返回模型生成的文本片段。
                .stream()
                // content：只提取 AssistantMessage 的文本内容。
                .content();
    }
}
