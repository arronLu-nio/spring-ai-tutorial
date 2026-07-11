package com.example.springaitutorial.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagIngestionService {

    private final VectorStore milvus;
    private final OpenSearchChunkIndex openSearch;
    private final RagProperties properties;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(500)
            .withMinChunkSizeChars(80)
            .withMinChunkLengthToEmbed(20)
            .withKeepSeparator(true)
            .build();

    public RagIngestionService(VectorStore milvus,
                               OpenSearchChunkIndex openSearch,
                               RagProperties properties) {
        this.milvus = milvus;
        this.openSearch = openSearch;
        this.properties = properties;
    }

    public IngestionResult ingest(MultipartFile file) throws IOException {
        // 1. 先校验上传文件，避免空文件或超大文件进入解析和向量化流程。
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > properties.getUploadMaxBytes()) {
            throw new IllegalArgumentException("文件超过大小限制");
        }

        // 2. 保存原始文件，便于后续追踪来源和问题排查。
        Path uploadDirectory = Path.of(properties.getUploadDirectory());
        Files.createDirectories(uploadDirectory);
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        Path target = uploadDirectory.resolve(UUID.randomUUID() + "-" + fileName);
        file.transferTo(target);

        // 3. 确保 OpenSearch 的关键词索引已经创建。
        openSearch.ensureIndex();

        // 4. Tika 根据文件类型自动解析 PDF、Word、文本等文件，统一得到 Document。
        // String 构造方法会按 classpath 资源读取；这里使用 FileSystemResource 明确读取本地磁盘文件。
        List<Document> sourceDocuments = new TikaDocumentReader(new FileSystemResource(target)).get();
        List<Document> chunks = new ArrayList<>();
        for (Document source : sourceDocuments) {
            // 5. 将长文档切成较小的片段，避免单次 Embedding 文本过长，也方便精准召回。
            List<Document> splitDocuments = splitter.apply(List.of(source));
            for (int i = 0; i < splitDocuments.size(); i++) {
                Document split = splitDocuments.get(i);
                HashMap<String, Object> metadata = new HashMap<>(split.getMetadata());
                // 6. 给每个切片补充来源和顺序信息，回答时用于展示引用来源。
                metadata.put("source", fileName);
                metadata.put("chunkIndex", i);
                metadata.put("documentId", target.getFileName().toString());
                chunks.add(Document.builder()
                        .id(UUID.randomUUID().toString())
                        .text(split.getText())
                        .metadata(metadata)
                        .build());
            }
        }

        // 7. 写入 Milvus：Spring AI 会调用 EmbeddingModel 生成向量，再保存向量和文本。
        milvus.add(chunks);

        // 8. 同时写入 OpenSearch：用于 BM25 关键词检索，后续与 Milvus 结果做混合召回。
        openSearch.saveAll(chunks);
        return new IngestionResult(fileName, chunks.size());
    }

    public record IngestionResult(String fileName, int chunkCount) {}
}
