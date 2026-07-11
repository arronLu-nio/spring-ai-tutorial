package com.example.springaitutorial.controller;

import com.example.springaitutorial.memory.SummaryChatMemory;
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
 * 演示“历史摘要 + 最近原始消息”的短期记忆。
 */
@RestController
public class SummaryMemoryController {

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    public SummaryMemoryController(ChatClient.Builder chatClientBuilder,
                                   RedisChatMemoryRepository repository) {
        this.chatClient = chatClientBuilder.build();

        ChatMemory delegate = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(100)
                .build();

        ChatClient summarizer = chatClientBuilder.build();
        ChatMemory summaryMemory = new SummaryChatMemory(
                delegate,
                summarizer,
                new JTokkitTokenCountEstimator(),
                800,
                4
        );
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(summaryMemory).build();
    }

    @GetMapping(value = "/ai/memory/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
