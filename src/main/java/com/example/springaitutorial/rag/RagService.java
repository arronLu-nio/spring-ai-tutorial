package com.example.springaitutorial.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final VectorStore milvus;
    private final OpenSearchChunkIndex openSearch;
    private final DashScopeReranker reranker;
    private final RagProperties properties;

    public RagService(ChatClient.Builder chatClientBuilder,
                      VectorStore milvus,
                      OpenSearchChunkIndex openSearch,
                      DashScopeReranker reranker,
                      RagProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.milvus = milvus;
        this.openSearch = openSearch;
        this.reranker = reranker;
        this.properties = properties;
    }

    public RagAnswer ask(String question) {
        List<Document> dense = milvus.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(properties.getHybridCandidateTopK())
                .similarityThreshold(properties.getMinRetrievalScore())
                .build());
        List<OpenSearchChunkIndex.LexicalHit> lexical = openSearch.search(
                question, properties.getHybridCandidateTopK());

        List<Document> fused = reciprocalRankFusion(dense, lexical);
        List<Document> reranked = reranker.rerank(question, fused).stream().limit(6).toList();
        String context = reranked.stream()
                .map(document -> "来源：" + document.getMetadata().getOrDefault("source", "未知")
                        + "\n" + document.getText())
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("没有检索到相关资料。");

        String answer = chatClient.prompt()
                .system("你是企业知识库问答助手。只能根据参考资料回答；资料不足时明确说不知道，不要编造。")
                .user("参考资料：\n" + context + "\n\n用户问题：" + question)
                .call()
                .content();

        return new RagAnswer(answer, reranked.stream()
                .map(document -> new Source(
                        String.valueOf(document.getMetadata().getOrDefault("source", "未知")),
                        document.getText(),
                        document.getScore()))
                .toList());
    }

    private List<Document> reciprocalRankFusion(List<Document> dense,
                                                 List<OpenSearchChunkIndex.LexicalHit> lexical) {
        Map<String, FusedDocument> fused = new LinkedHashMap<>();
        for (int i = 0; i < dense.size(); i++) {
            Document document = dense.get(i);
            fused.computeIfAbsent(document.getId(), ignored -> new FusedDocument(document))
                    .score += 1.0 / (properties.getHybridRrfK() + i + 1);
        }
        for (int i = 0; i < lexical.size(); i++) {
            OpenSearchChunkIndex.LexicalHit hit = lexical.get(i);
            Map<String, Object> metadata = new HashMap<>(hit.metadata());
            metadata.putIfAbsent("source", hit.source());
            Document document = Document.builder()
                    .id(hit.id())
                    .text(hit.content())
                    .metadata(metadata)
                    .build();
            fused.computeIfAbsent(hit.id(), ignored -> new FusedDocument(document))
                    .score += 1.0 / (properties.getHybridRrfK() + i + 1);
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedDocument::score).reversed())
                .map(FusedDocument::document)
                .limit(properties.getHybridCandidateTopK())
                .toList();
    }

    private static final class FusedDocument {
        private final Document document;
        private double score;
        private FusedDocument(Document document) { this.document = document; }
        private Document document() { return document; }
        private double score() { return score; }
    }

    public record RagAnswer(String answer, List<Source> sources) {}
    public record Source(String source, String content, Double score) {}
}
