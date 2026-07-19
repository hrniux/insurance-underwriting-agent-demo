package com.hrniux.underwriting.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalEvaluationServiceTest {

    private RetrievalEvaluationService evaluations;

    @BeforeEach
    void setUp() {
        KnowledgeService knowledge = new KnowledgeService(
                new InMemoryKnowledgeRepository(),
                new TextDocumentParser(),
                new ParagraphTextSplitter(),
                new InMemoryVectorStore(ignored -> new double[8]),
                200,
                20,
                4);
        knowledge.ingest(document("DOC-RAIN", "暴雨红色预警需要核验排水整改"));
        knowledge.ingest(document("DOC-FIRE", "消防喷淋失效需要人工复核"));
        evaluations = new RetrievalEvaluationService(knowledge);
    }

    @Test
    void calculatesRecallMrrAndThresholdFailureFromGoldenCases() {
        var report = evaluations.evaluate(List.of(
                testCase("暴雨命中", "暴雨排水整改", "DOC-RAIN"),
                testCase("缺失文档", "海啸巨灾模型", "DOC-NOT-INDEXED")),
                2,
                0.75,
                0.75);

        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(report.recallAtK()).isEqualTo(0.5);
        assertThat(report.meanReciprocalRank()).isEqualTo(0.5);
        assertThat(report.passed()).isFalse();
        assertThat(report.cases().getFirst()).satisfies(result -> {
            assertThat(result.recallAtK()).isEqualTo(1.0);
            assertThat(result.firstRelevantRank()).isEqualTo(1);
            assertThat(result.reciprocalRank()).isEqualTo(1.0);
        });
        assertThat(report.cases().get(1)).satisfies(result -> {
            assertThat(result.recallAtK()).isZero();
            assertThat(result.firstRelevantRank()).isNull();
            assertThat(result.reciprocalRank()).isZero();
        });
    }

    @Test
    void passesWhenBothQualityThresholdsAreMet() {
        var report = evaluations.evaluate(
                List.of(testCase("消防命中", "消防喷淋", "DOC-FIRE")),
                1,
                1.0,
                1.0);

        assertThat(report.passed()).isTrue();
        assertThat(report.recallAtK()).isEqualTo(1.0);
        assertThat(report.meanReciprocalRank()).isEqualTo(1.0);
    }

    @Test
    void rejectsAnInvalidEvaluationBoundary() {
        assertThatThrownBy(() -> evaluations.evaluate(List.of(), 4, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");
        assertThatThrownBy(() -> evaluations.evaluate(
                List.of(testCase("暴雨", "暴雨", "DOC-RAIN")), 21, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    private RetrievalEvaluationCase testCase(String name, String query, String expectedDocumentId) {
        return new RetrievalEvaluationCase(
                name, query, List.of(expectedDocumentId), null, "property");
    }

    private KnowledgeDocument document(String id, String content) {
        return new KnowledgeDocument(
                id,
                id + " 标题",
                DocumentType.UNDERWRITING_RULE,
                "PROPERTY",
                content,
                Map.of("source", "evaluation-test"));
    }
}
