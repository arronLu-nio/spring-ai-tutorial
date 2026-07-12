package com.example.springaitutorial.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import redis.clients.jedis.RedisClient;

/**
 * 把用户的自然语言问题改写成更适合检索的查询词。
 *
 * 例如："刚才说的那个怎么配置？" 会被改写成包含明确主题的查询，
 * 这样向量检索和关键词检索才有足够的信息。
 */
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;
    private final RedisClient redisClient;
    private final RagResilienceService resilience;
    private final RagProperties properties;

    public QueryRewriteService(ChatClient.Builder chatClientBuilder,
                               RedisClient redisClient,
                               RagResilienceService resilience,
                               RagProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.redisClient = redisClient;
        this.resilience = resilience;
        this.properties = properties;
    }

    public String rewrite(String question) {
        return rewrite("stateless", question, "");
    }

    public String rewrite(String question, String conversationHistory) {
        return rewrite("stateless", question, conversationHistory);
    }

    public String rewrite(String conversationId, String question, String conversationHistory) {
        String cacheKey = "rag:query-rewrite:" + sha256(
                conversationId + "\n" + question + "\n" + conversationHistory);
        String cached = redisClient.get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        String historyPrompt = conversationHistory == null || conversationHistory.isBlank()
                ? ""
                : "\n\n最近对话：\n" + conversationHistory;
        String rewritten = resilience.execute("query-rewrite", () -> chatClient.prompt()
                        .system("""
                                你是一个企业知识库检索查询改写器。
                                请把用户问题改写成适合向量检索和关键词检索的一条简洁查询。
                                要补全省略的主题，保留关键技术名词和限定条件。
                                只返回改写后的查询文本，不要解释，不要加引号，不要加 Markdown。
                                """)
                        .user("当前问题：\n" + question + historyPrompt)
                        .call()
                        .content(),
                () -> question);

        // 改写模型失败或返回空内容时，使用原问题保证 RAG 主流程仍然可用。
        if (rewritten == null || rewritten.isBlank()) {
            rewritten = question;
        }
        rewritten = rewritten.trim();
        redisClient.setex(cacheKey, properties.getQueryCacheTtlSeconds(), rewritten);
        return rewritten;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        }
        catch (Exception ex) {
            throw new IllegalStateException("无法生成缓存 key", ex);
        }
    }
}
