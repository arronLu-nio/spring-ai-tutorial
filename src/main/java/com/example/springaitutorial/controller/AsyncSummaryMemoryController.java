package com.example.springaitutorial.controller;

import com.example.springaitutorial.memory.AsyncSummaryChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 演示异步生成摘要，不阻塞当前 SSE 输出。
 */
@RestController
public class AsyncSummaryMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public AsyncSummaryMemoryController(ChatClient.Builder chatClientBuilder,
                                        RedisChatMemoryRepository repository) {
        this.chatClient = chatClientBuilder.build();

        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(100)
                .build();

        ChatMemory asyncSummaryMemory = new AsyncSummaryChatMemory(
                delegate,
                chatClientBuilder.build(),
                new JTokkitTokenCountEstimator(),
                800,
                4
        );
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(asyncSummaryMemory).build();
    }

    @GetMapping(value = "/ai/memory/summary-async", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
