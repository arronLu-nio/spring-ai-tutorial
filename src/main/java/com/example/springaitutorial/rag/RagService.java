package com.example.springaitutorial.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore milvus;
    private final OpenSearchChunkIndex openSearch;
    private final QueryRewriteService queryRewriteService;
    private final DashScopeReranker reranker;
    private final RagResilienceService resilience;
    private final RagProperties properties;

    public RagService(ChatClient.Builder chatClientBuilder,
                      ChatMemory chatMemory,
                      VectorStore milvus,
                      OpenSearchChunkIndex openSearch,
                      QueryRewriteService queryRewriteService,
                      DashScopeReranker reranker,
                      RagResilienceService resilience,
                      RagProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.milvus = milvus;
        this.openSearch = openSearch;
        this.queryRewriteService = queryRewriteService;
        this.reranker = reranker;
        this.resilience = resilience;
        this.properties = properties;
    }

    public RagAnswer ask(String question) {
        RetrievalResult retrieval = retrieve(question);
        String context = numberedContext(retrieval.documents());

        String answer = generateAnswer(() -> chatClient.prompt()
                .system("""
                        你是企业知识库问答助手。只能根据参考资料回答；资料不足时明确说不知道，不要编造。
                        参考资料已经编号为 [1]、[2]……每个有资料支持的事实句末尾必须标注对应引用，例如 [1] 或 [1][2]。
                        不要编造不存在的引用编号，也不要在没有资料支持时强行引用。
                        """)
                .user("参考资料：\n" + context + "\n\n用户问题：" + question)
                .call()
                .content(), "无资料时无法生成回答，请稍后重试。", "answer");

        return new RagAnswer(answer, toSources(retrieval.documents()));
    }

    /**
     * 带会话上下文的 RAG：历史消息只用于补全当前问题和辅助最终回答，
     * 真正检索仍然使用改写后的独立问题。
     */
    public RagAnswer ask(String conversationId, String question) {
        List<Message> history = chatMemory.get(conversationId);
        String historyText = formatRecentHistory(history);
        RetrievalResult retrieval = retrieve(conversationId, question, historyText);
        String context = numberedContext(retrieval.documents());

        String answer = generateAnswer(() -> chatClient.prompt()
                .system("""
                        你是企业知识库问答助手。只能根据参考资料回答；资料不足时明确说不知道，不要编造。
                        可以参考最近对话理解省略指代，但最终事实必须来自参考资料。
                        参考资料已经编号为 [1]、[2]……每个有资料支持的事实句末尾必须标注对应引用。
                        """)
                .user("最近对话：\n" + historyText
                        + "\n\n参考资料：\n" + context
                        + "\n\n当前问题：" + question)
                .call()
                .content(), "无资料时无法生成回答，请稍后重试。", "answer-with-memory");

        // RAG 对话不使用 ChatClient Advisor，回答完成后手动保存这一轮消息。
        chatMemory.add(conversationId, List.of(
                new UserMessage(question),
                new AssistantMessage(answer)));
        return new RagAnswer(answer, toSources(retrieval.documents()));
    }

    private String generateAnswer(java.util.function.Supplier<String> action,
                                  String fallback,
                                  String operation) {
        return resilience.execute(operation, action, () -> fallback);
    }

    /** 召回和重排由问答、效果评估共同使用。 */
    public RetrievalResult retrieve(String question) {
        return retrieve(question, "");
    }

    private RetrievalResult retrieve(String question, String historyText) {
        return retrieve("stateless", question, historyText);
    }

    private RetrievalResult retrieve(String conversationId, String question, String historyText) {
        // 只改写检索问题，最终回答仍然使用用户原问题，避免改变用户意图。
        String retrievalQuery = queryRewriteService.rewrite(conversationId, question, historyText);
        List<Document> dense = milvus.similaritySearch(SearchRequest.builder()
                .query(retrievalQuery)
                .topK(properties.getHybridCandidateTopK())
                .similarityThreshold(properties.getMinRetrievalScore())
                .build());
        List<OpenSearchChunkIndex.LexicalHit> lexical = openSearch.search(
                retrievalQuery, properties.getHybridCandidateTopK());
        List<Document> fused = reciprocalRankFusion(dense, lexical);
        List<Document> reranked = reranker.rerank(question, fused).stream().limit(6).toList();
        return new RetrievalResult(retrievalQuery, reranked);
    }

    private String formatRecentHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "无";
        }
        int start = Math.max(0, history.size() - 6);
        StringBuilder result = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            Message message = history.get(i);
            result.append(message.getMessageType()).append("：")
                    .append(message.getText()).append("\n");
        }
        return result.toString().trim();
    }

    private String numberedContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "没有检索到相关资料。";
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            context.append("[").append(i + 1).append("] 来源：")
                    .append(document.getMetadata().getOrDefault("source", "未知"))
                    .append("\n").append(document.getText());
            if (i < documents.size() - 1) {
                context.append("\n\n---\n\n");
            }
        }
        return context.toString();
    }

    private List<Source> toSources(List<Document> documents) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            sources.add(new Source(
                    i + 1,
                    String.valueOf(document.getMetadata().getOrDefault("source", "未知")),
                    document.getText(),
                    document.getScore()));
        }
        return sources;
    }

    public Evaluation evaluate(String question, String expectedSource) {
        RetrievalResult retrieval = retrieve(question);
        int rank = 0;
        for (int i = 0; i < retrieval.documents().size(); i++) {
            Object source = retrieval.documents().get(i).getMetadata().get("source");
            if (expectedSource.equals(String.valueOf(source))) {
                rank = i + 1;
                break;
            }
        }
        return new Evaluation(question, retrieval.retrievalQuery(), expectedSource,
                rank > 0, rank == 0 ? 0 : 1.0 / rank,
                toSources(retrieval.documents()));
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

    public record RetrievalResult(String retrievalQuery, List<Document> documents) {}
    public record RagAnswer(String answer, List<Source> sources) {}
    public record Source(int citation, String source, String content, Double score) {}
    public record Evaluation(String question, String retrievalQuery, String expectedSource,
                             boolean hit, double reciprocalRank, List<Source> sources) {}
}
