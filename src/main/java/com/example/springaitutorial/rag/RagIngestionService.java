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
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > properties.getUploadMaxBytes()) {
            throw new IllegalArgumentException("文件超过大小限制");
        }

        Path uploadDirectory = Path.of(properties.getUploadDirectory());
        Files.createDirectories(uploadDirectory);
        String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        Path target = uploadDirectory.resolve(UUID.randomUUID() + "-" + fileName);
        file.transferTo(target);

        openSearch.ensureIndex();
        List<Document> sourceDocuments = new TikaDocumentReader(target.toFile().getAbsolutePath()).get();
        List<Document> chunks = new ArrayList<>();
        for (Document source : sourceDocuments) {
            List<Document> splitDocuments = splitter.apply(List.of(source));
            for (int i = 0; i < splitDocuments.size(); i++) {
                Document split = splitDocuments.get(i);
                HashMap<String, Object> metadata = new HashMap<>(split.getMetadata());
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

        milvus.add(chunks);
        openSearch.saveAll(chunks);
        return new IngestionResult(fileName, chunks.size());
    }

    public record IngestionResult(String fileName, int chunkCount) {}
}
