package com.hrniux.underwriting.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.api.ApiDtos.KnowledgeDocumentRequest;
import com.hrniux.underwriting.api.ApiDtos.KnowledgeSearchRequest;
import com.hrniux.underwriting.rag.KnowledgeDocument;
import com.hrniux.underwriting.rag.KnowledgeIngestionResult;
import com.hrniux.underwriting.rag.KnowledgeService;
import com.hrniux.underwriting.rag.RetrievalHit;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/documents")
    public ResponseEntity<KnowledgeIngestionResult> ingest(@Valid @RequestBody KnowledgeDocumentRequest request) {
        KnowledgeIngestionResult result = knowledge.ingest(request.toDomain());
        return ResponseEntity.created(URI.create("/api/v1/knowledge/documents/" + result.documentId())).body(result);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument> list() {
        return knowledge.list();
    }

    @PostMapping("/search")
    public List<RetrievalHit> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return knowledge.search(request.query(), request.topK(), request.documentType(), request.productCode());
    }
}
