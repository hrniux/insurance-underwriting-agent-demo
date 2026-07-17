package com.hrniux.underwriting.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hrniux.underwriting.demo.DemoScenarioRepository;
import com.hrniux.underwriting.model.DeterministicMockModelGateway;
import com.hrniux.underwriting.prompt.PromptTemplateService;
import com.hrniux.underwriting.rag.KnowledgeService;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.UnderwritingRuleEngine;
import com.hrniux.underwriting.session.InMemorySessionRepository;
import com.hrniux.underwriting.session.SessionRole;
import com.hrniux.underwriting.session.SessionService;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;
import com.hrniux.underwriting.tool.FakeUnderwritingFactTools;
import com.hrniux.underwriting.tool.ToolCallStatus;
import com.hrniux.underwriting.tool.ToolName;
import com.hrniux.underwriting.tool.ToolRegistry;

@SpringBootTest
class UnderwritingAgentOrchestratorTest {

    @Autowired
    private UnderwritingAgentOrchestrator orchestrator;

    @Autowired
    private SessionService sessions;

    @Test
    void executesTheExplainableSevenStepPipelineAndPersistsTheResult() {
        UnderwritingEvaluation evaluation = orchestrator.evaluate(
                new EvaluationRequest(null, "P-1001", "暴雨风险较高，这张仓库财产险能否承保？"));

        assertThat(evaluation.sessionId()).isNotBlank();
        assertThat(evaluation.stepTraces()).extracting(StepTrace::step).containsExactly(AgentStep.values());
        assertThat(evaluation.stepTraces()).extracting(StepTrace::status)
                .containsOnly(StepStatus.SUCCESS);
        assertThat(evaluation.toolTraces()).hasSize(6);
        assertThat(evaluation.toolTraces()).extracting(trace -> trace.toolName())
                .contains(ToolName.GET_POLICY, ToolName.GET_QUOTATION, ToolName.GET_UNDERWRITING_HISTORY,
                        ToolName.GET_SURVEY_REPORT, ToolName.GET_DISASTER_RISK, ToolName.VALIDATE_RULES);
        assertThat(evaluation.evidence()).isNotEmpty();
        assertThat(evaluation.decision().ordinal()).isGreaterThanOrEqualTo(Decision.MANUAL_REVIEW.ordinal());
        assertThat(orchestrator.getEvaluation(evaluation.id())).isEqualTo(evaluation);
        assertThat(sessions.getSession(evaluation.sessionId()).messages())
                .extracting(message -> message.role())
                .containsExactly(SessionRole.USER, SessionRole.ASSISTANT);
    }

    @Test
    void raisesTheDecisionFloorWhenKnowledgeEvidenceIsMissing() {
        KnowledgeService emptyKnowledge = mock(KnowledgeService.class);
        when(emptyKnowledge.search(anyString(), anyInt(), isNull(), anyString())).thenReturn(List.of());
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        when(prompts.preview(anyString(), any(Map.class))).thenReturn("rendered prompt");

        UnderwritingAgentOrchestrator isolated = isolatedOrchestrator(
                emptyKnowledge,
                prompts,
                new ToolRegistry(new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault())));

        UnderwritingEvaluation evaluation = isolated.evaluate(
                new EvaluationRequest(null, "P-2001", "这张低风险办公楼保单是否可以承保？"));

        assertThat(evaluation.evidence()).isEmpty();
        assertThat(evaluation.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(evaluation.summary()).contains("人工复核");
    }

    @Test
    void recordsTheFailedStepBeforePropagatingACriticalToolFailure() {
        ToolRegistry failingTools = mock(ToolRegistry.class);
        when(failingTools.invoke(ToolName.GET_POLICY, "P-MISSING"))
                .thenThrow(new ResourceNotFoundException("POLICY_NOT_FOUND", "P-MISSING"));

        UnderwritingAgentOrchestrator isolated = isolatedOrchestrator(
                mock(KnowledgeService.class),
                mock(PromptTemplateService.class),
                failingTools);

        assertThatThrownBy(() -> isolated.evaluate(
                new EvaluationRequest(null, "P-MISSING", "是否承保？")))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(isolated.lastStepTraces()).last().satisfies(trace -> {
            assertThat(trace.step()).isEqualTo(AgentStep.BUSINESS_DATA_COLLECTION);
            assertThat(trace.status()).isEqualTo(StepStatus.FAILED);
            assertThat(trace.errorCode()).isEqualTo("POLICY_NOT_FOUND");
        });
    }

    private UnderwritingAgentOrchestrator isolatedOrchestrator(
            KnowledgeService knowledge,
            PromptTemplateService prompts,
            ToolRegistry tools) {
        AtomicInteger sequence = new AtomicInteger();
        return new UnderwritingAgentOrchestrator(
                new SessionService(new InMemorySessionRepository()),
                knowledge,
                prompts,
                tools,
                new UnderwritingRuleEngine(),
                new DeterministicMockModelGateway("test-model"),
                new InMemoryEvaluationRepository(),
                Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC),
                () -> "EVAL-" + sequence.incrementAndGet());
    }
}
