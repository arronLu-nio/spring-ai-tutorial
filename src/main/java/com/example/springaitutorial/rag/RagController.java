package com.example.springaitutorial.rag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RagController {

    private final RagIngestionService ingestionService;
    private final RagService ragService;

    public RagController(RagIngestionService ingestionService, RagService ragService) {
        this.ingestionService = ingestionService;
        this.ragService = ragService;
    }

    @PostMapping(value = "/api/rag/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RagIngestionService.IngestionResult upload(@RequestParam("file") MultipartFile file)
            throws Exception {
        return ingestionService.ingest(file);
    }

    @GetMapping("/api/rag/ask")
    public RagService.RagAnswer ask(@RequestParam String question) {
        return ragService.ask(question);
    }

    @GetMapping("/api/rag/chat")
    public RagService.RagAnswer chat(@RequestParam String conversationId,
                                     @RequestParam String question) {
        return ragService.ask(conversationId, question);
    }

    @GetMapping("/api/rag/evaluate")
    public RagService.Evaluation evaluate(@RequestParam String question,
                                          @RequestParam String expectedSource) {
        return ragService.evaluate(question, expectedSource);
    }
}
