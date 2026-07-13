package com.hrniux.underwriting.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSeedLoader implements ApplicationRunner {

    private static final List<SeedDocument> DOCUMENTS = List.of(
            new SeedDocument("CLAUSE-PROPERTY-001", "企财险产品条款示例",
                    DocumentType.PRODUCT_CLAUSE, "classpath:knowledge/property-clauses.md"),
            new SeedDocument("RULE-RAIN-001", "暴雨与洪水风险核保规则",
                    DocumentType.UNDERWRITING_RULE, "classpath:knowledge/rainstorm-rules.md"),
            new SeedDocument("GUIDE-WAREHOUSE-001", "仓储风险查勘说明",
                    DocumentType.RISK_GUIDE, "classpath:knowledge/warehouse-risk-guide.md"),
            new SeedDocument("CASE-PROPERTY-001", "企财险历史核保案例",
                    DocumentType.HISTORICAL_CASE, "classpath:knowledge/historical-cases.md"));

    private final KnowledgeService knowledgeService;
    private final ResourceLoader resourceLoader;

    public KnowledgeSeedLoader(KnowledgeService knowledgeService, ResourceLoader resourceLoader) {
        this.knowledgeService = knowledgeService;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public synchronized void seed() {
        if (!knowledgeService.list().isEmpty()) {
            return;
        }
        DOCUMENTS.forEach(spec -> knowledgeService.ingest(new KnowledgeDocument(
                spec.id(),
                spec.title(),
                spec.type(),
                "PROPERTY",
                read(spec.resourcePath()),
                Map.of("source", "fictional-demo", "resource", spec.resourcePath()))));
    }

    private String read(String path) {
        try {
            return resourceLoader.getResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read seed knowledge: " + path, exception);
        }
    }

    private record SeedDocument(String id, String title, DocumentType type, String resourcePath) {
    }
}
