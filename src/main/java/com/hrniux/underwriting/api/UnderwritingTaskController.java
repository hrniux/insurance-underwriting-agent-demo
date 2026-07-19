package com.hrniux.underwriting.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.agent.EvaluationSubmissionService;
import com.hrniux.underwriting.api.ApiDtos.EvaluationApiRequest;
import com.hrniux.underwriting.task.UnderwritingTask;
import com.hrniux.underwriting.task.UnderwritingTaskService;
import com.hrniux.underwriting.task.UnderwritingTaskSubmissionResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/underwriting/tasks")
public class UnderwritingTaskController {

    private final UnderwritingTaskService tasks;

    public UnderwritingTaskController(UnderwritingTaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    public ResponseEntity<UnderwritingTask> submit(
            @RequestHeader(name = EvaluationSubmissionService.IDEMPOTENCY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody EvaluationApiRequest request) {
        UnderwritingTaskSubmissionResult result = tasks.submit(request.toDomain(), idempotencyKey);
        String location = "/api/v1/underwriting/tasks/" + result.task().id();
        ResponseEntity.BodyBuilder response = result.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.accepted().location(URI.create(location));
        return response.header(EvaluationSubmissionService.REPLAY_HEADER, Boolean.toString(result.replayed()))
                .body(result.task());
    }

    @GetMapping("/{taskId}")
    public UnderwritingTask get(@PathVariable String taskId) {
        return tasks.get(taskId);
    }

    @GetMapping
    public List<UnderwritingTask> list() {
        return tasks.list();
    }
}
