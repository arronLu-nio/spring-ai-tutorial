package com.example.springaitutorial.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                // 使用 Redis Stack 保存消息，应用重启后会话仍然存在
                .chatMemoryRepository(repository)
                // 最多保留 10 条消息，超出后淘汰较早的消息
                .maxMessages(10)
                .build();
    }
}
