package com.example.springaitutorial.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenSearch 的轻量关键词索引。Milvus 负责向量检索，这里负责 BM25 检索。
 */
@Component
public class OpenSearchChunkIndex {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public OpenSearchChunkIndex(RestClient.Builder builder,
                                ObjectMapper objectMapper,
                                RagProperties properties) {
        this.client = builder.baseUrl(properties.getOpensearchUrl()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void ensureIndex() {
        try {
            client.put()
                    .uri("/" + properties.getOpensearchIndex())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("mappings", Map.of("properties", Map.of(
                            "content", Map.of("type", "text"),
                            "source", Map.of("type", "keyword"),
                            "chunkIndex", Map.of("type", "integer")
                    ))))
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (Exception ignored) {
            // 400 通常表示索引已经存在；OpenSearch 不可用时由查询/上传时再报错。
        }
    }

    public void saveAll(List<Document> documents) {
        for (Document document : documents) {
            client.put()
                    .uri("/" + properties.getOpensearchIndex() + "/_doc/" + document.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "id", document.getId(),
                            "content", document.getText(),
                            "source", String.valueOf(document.getMetadata().getOrDefault("source", "")),
                            "chunkIndex", document.getMetadata().getOrDefault("chunkIndex", 0),
                            "metadata", document.getMetadata()))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public List<LexicalHit> search(String query, int topK) {
        JsonNode root = client.post()
                .uri("/" + properties.getOpensearchIndex() + "/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "size", topK,
                        "query", Map.of("multi_match", Map.of(
                                "query", query,
                                "fields", List.of("content^2", "source")
                        ))
                ))
                .retrieve()
                .body(JsonNode.class);

        List<LexicalHit> hits = new ArrayList<>();
        if (root == null || root.path("hits").path("hits").isMissingNode()) {
            return hits;
        }
        for (JsonNode hit : root.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            hits.add(new LexicalHit(
                    textOrDefault(source, "id", textOrDefault(hit, "_id", "")),
                    textOrDefault(source, "content", ""),
                    textOrDefault(source, "source", ""),
                    numberOrDefault(hit, "_score", 0),
                    metadataOrEmpty(source)
            ));
        }
        return hits;
    }

    /**
     * Jackson 3 对 MissingNode 调用 asText() 会抛异常，因此不能直接写
     * source.path("field").asText(defaultValue)。先判断字段是否真的存在。
     */
    private String textOrDefault(JsonNode parent, String fieldName, String defaultValue) {
        JsonNode value = parent == null ? null : parent.get(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? defaultValue : value.asText();
    }

    private double numberOrDefault(JsonNode parent, String fieldName, double defaultValue) {
        JsonNode value = parent == null ? null : parent.get(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? defaultValue : value.asDouble();
    }

    private Map<String, Object> metadataOrEmpty(JsonNode source) {
        JsonNode metadata = source == null ? null : source.get("metadata");
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(metadata, new TypeReference<>() {});
    }

    public record LexicalHit(String id, String content, String source, double score,
                             Map<String, Object> metadata) {}
}
