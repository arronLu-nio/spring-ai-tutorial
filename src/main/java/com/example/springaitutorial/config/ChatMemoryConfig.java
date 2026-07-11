package com.example.springaitutorial.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

@Configuration
public class ChatMemoryConfig {

    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password}") String password) {
        return RedisClient.builder()
                .hostAndPort(host, port)
                // Spring AI 的 Redis 自动配置不会读取密码，这里显式配置认证。
                .clientConfig(DefaultJedisClientConfig.builder()
                        .password(password)
                        .build())
                .build();
    }

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(RedisClient redisClient) {
        return RedisChatMemoryRepository.builder()
                .jedisClient(redisClient)
                .keyPrefix("spring-ai-memory:")
                .timeToLive(java.time.Duration.ofHours(24))
                .build();
    }

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
