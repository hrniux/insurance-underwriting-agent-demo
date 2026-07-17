package com.hrniux.underwriting.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.agent.UnderwritingAgentOrchestrator;
import com.hrniux.underwriting.agent.UnderwritingEvaluation;
import com.hrniux.underwriting.api.ApiDtos.EvaluationApiRequest;
import com.hrniux.underwriting.report.UnderwritingMarkdownReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/underwriting/evaluations")
public class UnderwritingEvaluationController {

    private final UnderwritingAgentOrchestrator orchestrator;
    private final UnderwritingMarkdownReportService reportService;

    public UnderwritingEvaluationController(
            UnderwritingAgentOrchestrator orchestrator,
            UnderwritingMarkdownReportService reportService) {
        this.orchestrator = orchestrator;
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<UnderwritingEvaluation> evaluate(@Valid @RequestBody EvaluationApiRequest request) {
        UnderwritingEvaluation evaluation = orchestrator.evaluate(request.toDomain());
        return ResponseEntity.created(URI.create("/api/v1/underwriting/evaluations/" + evaluation.id()))
                .body(evaluation);
    }

    @GetMapping("/{evaluationId}")
    public UnderwritingEvaluation get(@PathVariable String evaluationId) {
        return orchestrator.getEvaluation(evaluationId);
    }

    @GetMapping(value = "/{evaluationId}/report", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> report(@PathVariable String evaluationId) {
        UnderwritingEvaluation evaluation = orchestrator.getEvaluation(evaluationId);
        String filename = "underwriting-report-" + evaluation.id() + ".md";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(reportService.render(evaluation));
    }

    @GetMapping
    public List<UnderwritingEvaluation> list() {
        return orchestrator.listEvaluations();
    }
}
