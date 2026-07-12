package com.example.springaitutorial.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DashScope Rerank 的可选适配器。接口失败时由上层回退到 RRF 结果。
 */
@Component
public class DashScopeReranker {

    private final RestClient client = RestClient.builder().build();
    private final RagProperties properties;
    private final RagResilienceService resilience;

    public DashScopeReranker(RagProperties properties, RagResilienceService resilience) {
        this.properties = properties;
        this.resilience = resilience;
    }

    public List<Document> rerank(String query, List<Document> candidates) {
        if (properties.getRerankerApiUrl() == null || properties.getRerankerApiUrl().isBlank()
                || candidates.isEmpty()) {
            return candidates;
        }

        return resilience.execute("rerank", () -> doRerank(query, candidates), () -> candidates);
    }

    private List<Document> doRerank(String query, List<Document> candidates) {
        try {
            List<String> documents = candidates.stream().map(Document::getText).toList();
            JsonNode root = client.post()
                    .uri(properties.getRerankerApiUrl())
                    .header("Authorization", "Bearer " + properties.getRerankerApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.getRerankerModel(),
                            "input", Map.of("query", query, "documents", documents),
                            "parameters", Map.of("top_n", candidates.size(), "return_documents", false)
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode results = root == null ? null : root.path("output").path("results");
            if (results == null || results.isMissingNode() || !results.isArray()) {
                results = root == null ? null : root.path("results");
            }
            if (results == null || !results.isArray()) {
                return candidates;
            }

            List<ScoredDocument> scored = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.path("index").asInt(-1);
                double score = result.path("relevance_score").asDouble(
                        result.path("score").asDouble(0));
                if (index >= 0 && index < candidates.size() && score >= properties.getMinRerankScore()) {
                    scored.add(new ScoredDocument(candidates.get(index), score));
                }
            }
            scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
            return scored.stream().map(ScoredDocument::document).toList();
        }
        catch (Exception ignored) {
            return candidates;
        }
    }

    private record ScoredDocument(Document document, double score) {}
}
