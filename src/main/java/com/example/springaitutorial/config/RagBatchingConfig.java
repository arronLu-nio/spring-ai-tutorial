package com.example.springaitutorial.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashScope Embedding 接口单批最多接收 10 条文本，因此按条数切分请求。
 */
@Configuration
public class RagBatchingConfig {

    private static final int MAX_DOCUMENTS_PER_BATCH = 10;

    @Bean
    public BatchingStrategy ragBatchingStrategy() {
        return documents -> {
            List<List<Document>> batches = new ArrayList<>();
            for (int start = 0; start < documents.size(); start += MAX_DOCUMENTS_PER_BATCH) {
                int end = Math.min(start + MAX_DOCUMENTS_PER_BATCH, documents.size());
                batches.add(documents.subList(start, end));
            }
            return batches;
        };
    }
}
