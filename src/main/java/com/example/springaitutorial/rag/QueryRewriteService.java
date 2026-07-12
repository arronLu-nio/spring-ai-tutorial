package com.example.springaitutorial.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 把用户的自然语言问题改写成更适合检索的查询词。
 *
 * 例如："刚才说的那个怎么配置？" 会被改写成包含明确主题的查询，
 * 这样向量检索和关键词检索才有足够的信息。
 */
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;

    public QueryRewriteService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String rewrite(String question) {
        String rewritten = chatClient.prompt()
                .system("""
                        你是一个企业知识库检索查询改写器。
                        请把用户问题改写成适合向量检索和关键词检索的一条简洁查询。
                        要补全省略的主题，保留关键技术名词和限定条件。
                        只返回改写后的查询文本，不要解释，不要加引号，不要加 Markdown。
                        """)
                .user(question)
                .call()
                .content();

        // 改写模型失败或返回空内容时，使用原问题保证 RAG 主流程仍然可用。
        if (rewritten == null || rewritten.isBlank()) {
            return question;
        }
        return rewritten.trim();
    }
}
