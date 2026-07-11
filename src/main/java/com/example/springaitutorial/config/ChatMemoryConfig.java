package com.example.springaitutorial.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

import com.example.springaitutorial.session.ConversationService;

import java.util.List;
import java.util.Map;

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
                // 使用新的索引名，避免复用之前由通配 metadata schema 创建的旧索引。
                .indexName("chat-memory-idx-v2")
                .keyPrefix("spring-ai-memory:")
                // 只索引确实需要查询的字符串字段，避免 $.metadata.* 把数组、对象也当成 TEXT 索引。
                // 未列出的 metadata 仍然会完整保存在 Redis JSON 中，只是不参与搜索索引。
                .metadataFields(List.of(
                        Map.of("name", "role", "type", "tag")
                ))
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

    @Bean
    public ConversationService conversationService(RedisClient redisClient, ChatMemory chatMemory) {
        return new ConversationService(redisClient, chatMemory);
    }
}
