package com.example.demo.controller;

import com.example.demo.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class KnowledgeController {

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(
            value = "/documents/ingest",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<RagService.IngestionResult> ingest(@RequestPart("file") MultipartFile file) {
        return Mono.fromCallable(() -> {
                    if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("Please select a non-empty PDF file");
                    }

                    String fileName = safePdfFileName(file.getOriginalFilename());
                    ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            return fileName;
                        }
                    };
                    return ragService.ingestPdf(resource);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/search")
    public Mono<List<SearchResult>> search(
            @RequestParam String query,
            @RequestParam(required = false) Integer topK) {
        return Mono.fromCallable(() -> {
                    var documents = topK == null
                            ? ragService.search(query)
                            : ragService.search(query, Math.max(1, Math.min(topK, 20)));
                    return documents.stream()
                            .map(SearchResult::from)
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public record SearchResult(
            String id,
            String text,
            Double score,
            Map<String, Object> metadata) {

        static SearchResult from(Document document) {
            return new SearchResult(
                    document.getId(),
                    document.getText(),
                    document.getScore(),
                    document.getMetadata());
        }
    }

    private static String safePdfFileName(String originalFilename) {
        String fileName = StringUtils.cleanPath(
                originalFilename == null ? "" : originalFilename);
        int separatorIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        fileName = fileName.substring(separatorIndex + 1);

        if (fileName.isBlank()
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }
        return fileName;
    }
}
