package com.hrniux.underwriting.api;

import java.net.URI;
import java.util.List;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/underwriting/evaluations")
public class UnderwritingEvaluationController {

    private final UnderwritingAgentOrchestrator orchestrator;

    public UnderwritingEvaluationController(UnderwritingAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
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

    @GetMapping
    public List<UnderwritingEvaluation> list() {
        return orchestrator.listEvaluations();
    }
}
