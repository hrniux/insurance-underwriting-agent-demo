# Insurance Underwriting Agent Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 21 Spring Boot demo that runs an explainable property-insurance underwriting Agent with session management, RAG, deterministic rules, prompt versions, switchable LLM gateways, and six real MCP tools.

**Architecture:** Use a modular monolith with domain records and repository/gateway ports around in-memory demo adapters. `UnderwritingAgentOrchestrator` coordinates fact tools, knowledge retrieval, rules, prompt rendering, and the selected model gateway; Spring MVC exposes REST APIs and Spring AI exposes the same tool services over Streamable HTTP MCP.

**Tech Stack:** Java 21, Maven, Spring Boot 4.1.0, Spring AI 2.0.0, Spring MVC, Validation, Actuator, Spring Retry, springdoc-openapi 3.0.3, JUnit 5, AssertJ, Mockito, MockWebServer, Docker.

---

## File map

The implementation creates these focused units:

- `pom.xml`: dependency management, Java 21 compiler and test/build plugins.
- `src/main/java/com/hrniux/underwriting/UnderwritingAgentApplication.java`: application entry point and configuration properties scanning.
- `src/main/java/com/hrniux/underwriting/shared/config/AgentProperties.java`: typed model, RAG and fallback settings.
- `src/main/java/com/hrniux/underwriting/shared/error/*`: domain exceptions and RFC 9457 error mapping.
- `src/main/java/com/hrniux/underwriting/session/*`: session domain, repository port, memory adapter and service.
- `src/main/java/com/hrniux/underwriting/rag/*`: knowledge domain, splitter, embedding, vector store and retrieval service.
- `src/main/java/com/hrniux/underwriting/prompt/*`: template versions, repository and strict renderer.
- `src/main/java/com/hrniux/underwriting/tool/*`: typed tool contracts, fake internal-system adapter, registry and traces.
- `src/main/java/com/hrniux/underwriting/rule/*`: deterministic rule interface, five rules and aggregation engine.
- `src/main/java/com/hrniux/underwriting/model/*`: model contract, mock provider, OpenAI-compatible HTTP provider, retry classifier and router.
- `src/main/java/com/hrniux/underwriting/agent/*`: evaluation domain, trace records, repository and orchestrator.
- `src/main/java/com/hrniux/underwriting/api/*`: REST DTOs/controllers and OpenAPI annotations.
- `src/main/java/com/hrniux/underwriting/tool/mcp/UnderwritingMcpTools.java`: six `@McpTool` methods delegating to the registry.
- `src/main/resources/application.yml`: default no-key runnable configuration and Streamable MCP protocol.
- `src/main/resources/knowledge/*.md`: fictional product, rule, guide and historical-case documents.
- `src/test/java/com/hrniux/underwriting/**/*Test.java`: unit and integration tests.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/API_EXAMPLES.md`, `docs/INTERVIEW_GUIDE.md`: operator and interview documentation.
- `scripts/demo.sh`, `Dockerfile`, `.dockerignore`, `.gitignore`: repeatable delivery and smoke test assets.

## Task 1: Bootstrap a healthy no-key application

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/com/hrniux/underwriting/UnderwritingAgentApplication.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/config/AgentProperties.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/hrniux/underwriting/ApplicationSmokeTest.java`

- [ ] **Step 1: Write the failing application context test**

```java
@SpringBootTest
class ApplicationSmokeTest {
    @Test
    void startsWithoutAnApiKey() {
    }
}
```

- [ ] **Step 2: Run the test and verify it fails because no Maven project exists**

Run: `mvn -q -Dtest=ApplicationSmokeTest test`

Expected: non-zero exit because `pom.xml` or the application class is missing.

- [ ] **Step 3: Add the Maven build and application configuration**

Use Spring Boot parent `4.1.0`, import `org.springframework.ai:spring-ai-bom:2.0.0`, and add:

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
<dependency><groupId>org.springframework.retry</groupId><artifactId>spring-retry</artifactId></dependency>
<dependency><groupId>org.springframework.ai</groupId><artifactId>spring-ai-starter-mcp-server-webmvc</artifactId></dependency>
<dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>3.0.3</version></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>com.squareup.okhttp3</groupId><artifactId>mockwebserver</artifactId><scope>test</scope></dependency>
```

Bind these defaults in `AgentProperties`: provider `mock`, model `deterministic-underwriter-v1`, connect timeout two seconds, read timeout fifteen seconds, maximum attempts three, fallback disabled, embedding dimensions 256, chunk size 500, overlap 80 and top K four.

Configure:

```yaml
spring:
  application:
    name: insurance-underwriting-agent-demo
  ai:
    mcp:
      server:
        name: underwriting-business-tools
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC
management:
  endpoints:
    web:
      exposure:
        include: health,info
app:
  agent:
    model:
      provider: mock
      model: deterministic-underwriter-v1
```

- [ ] **Step 4: Run the smoke test and verify it passes**

Run: `mvn -q -Dtest=ApplicationSmokeTest test`

Expected: one passing test and exit code zero.

- [ ] **Step 5: Commit the bootstrap**

```bash
git add pom.xml .gitignore src/main src/test
git commit -m "build: bootstrap underwriting agent service"
```

## Task 2: Implement session management

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/session/SessionRole.java`
- Create: `src/main/java/com/hrniux/underwriting/session/SessionMessage.java`
- Create: `src/main/java/com/hrniux/underwriting/session/UnderwritingSession.java`
- Create: `src/main/java/com/hrniux/underwriting/session/SessionRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/session/InMemorySessionRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/session/SessionService.java`
- Test: `src/test/java/com/hrniux/underwriting/session/SessionServiceTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Test that `createSession()` returns an ID beginning with `SES-`, starts with no messages, and that `appendMessage(id, USER, "请评估保单")` stores one immutable message and advances `updatedAt`.

```java
@Test
void createsAndAppendsMessages() {
    UnderwritingSession created = service.createSession();
    UnderwritingSession updated = service.appendMessage(created.id(), SessionRole.USER, "请评估保单");
    assertThat(created.id()).startsWith("SES-");
    assertThat(updated.messages()).singleElement().extracting(SessionMessage::content)
            .isEqualTo("请评估保单");
    assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
}
```

- [ ] **Step 2: Run the test and verify missing session types fail compilation**

Run: `mvn -q -Dtest=SessionServiceTest test`

Expected: compilation failure naming `SessionService` or the session records.

- [ ] **Step 3: Implement the records, repository port and thread-safe adapter**

Use defensive `List.copyOf`, `Clock` injection and `ConcurrentHashMap`. Throw `ResourceNotFoundException("SESSION_NOT_FOUND", id)` for unknown IDs. Keep ID generation behind a `Supplier<String>` constructor so the test can be deterministic.

- [ ] **Step 4: Run session tests**

Run: `mvn -q -Dtest=SessionServiceTest test`

Expected: all lifecycle and not-found tests pass.

- [ ] **Step 5: Commit session management**

```bash
git add src/main/java/com/hrniux/underwriting/session src/test/java/com/hrniux/underwriting/session
git commit -m "feat: add underwriting session management"
```

## Task 3: Build deterministic RAG primitives

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/rag/DocumentType.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/KnowledgeDocument.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/DocumentChunk.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/RetrievalHit.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/TextDocumentParser.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/ParagraphTextSplitter.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/EmbeddingService.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/HashEmbeddingService.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/VectorStore.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/InMemoryVectorStore.java`
- Test: `src/test/java/com/hrniux/underwriting/rag/ParagraphTextSplitterTest.java`
- Test: `src/test/java/com/hrniux/underwriting/rag/HashEmbeddingServiceTest.java`
- Test: `src/test/java/com/hrniux/underwriting/rag/InMemoryVectorStoreTest.java`

- [ ] **Step 1: Write failing splitter, embedding and ranking tests**

Cover these exact invariants:

```java
assertThat(splitter.split(document, 30, 5)).allSatisfy(chunk ->
        assertThat(chunk.content().length()).isLessThanOrEqualTo(30));
assertThat(embedding.embed("暴雨 仓库")).containsExactly(embedding.embed("暴雨 仓库"));
assertThat(l2Norm(embedding.embed("暴雨 仓库"))).isCloseTo(1.0, within(1.0e-9));
assertThat(store.search("暴雨风险", 2, DocumentType.UNDERWRITING_RULE, "PROPERTY"))
        .extracting(hit -> hit.document().id())
        .containsExactly("DOC-RAIN");
```

- [ ] **Step 2: Run the RAG tests and verify they fail**

Run: `mvn -q -Dtest='ParagraphTextSplitterTest,HashEmbeddingServiceTest,InMemoryVectorStoreTest' test`

Expected: compilation failure for missing RAG classes.

- [ ] **Step 3: Implement paragraph-aware chunking and hash embeddings**

Tokenize lower-cased Latin words and individual CJK code points, hash each token with SHA-256, select a dimension from the first four bytes, use the next byte for a positive or negative sign, accumulate counts and L2-normalize the vector. Empty input returns a zero vector.

Split normalized text at blank lines first; if a paragraph exceeds `maxCharacters`, use fixed windows. The next chunk begins `overlapCharacters` before the prior chunk end. Reject overlap values that are negative or not smaller than the maximum.

- [ ] **Step 4: Implement cosine ranking and metadata filtering**

Store an immutable chunk, its vector and metadata in a `ConcurrentHashMap`. Search filters by optional document type and product code, computes dot product over normalized vectors, excludes non-positive scores, sorts by descending score then chunk ID, and limits to Top-K.

- [ ] **Step 5: Run RAG tests**

Run: `mvn -q -Dtest='ParagraphTextSplitterTest,HashEmbeddingServiceTest,InMemoryVectorStoreTest' test`

Expected: all RAG primitive tests pass.

- [ ] **Step 6: Commit RAG primitives**

```bash
git add src/main/java/com/hrniux/underwriting/rag src/test/java/com/hrniux/underwriting/rag
git commit -m "feat: add deterministic RAG primitives"
```

## Task 4: Add knowledge ingestion, retrieval and seed documents

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/rag/KnowledgeRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/InMemoryKnowledgeRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/KnowledgeService.java`
- Create: `src/main/java/com/hrniux/underwriting/rag/KnowledgeSeedLoader.java`
- Create: `src/main/resources/knowledge/property-clauses.md`
- Create: `src/main/resources/knowledge/rainstorm-rules.md`
- Create: `src/main/resources/knowledge/warehouse-risk-guide.md`
- Create: `src/main/resources/knowledge/historical-cases.md`
- Test: `src/test/java/com/hrniux/underwriting/rag/KnowledgeServiceTest.java`
- Test: `src/test/java/com/hrniux/underwriting/rag/KnowledgeSeedLoaderTest.java`

- [ ] **Step 1: Write failing ingestion and retrieval tests**

Create a document with ID `RULE-RAIN-001`, type `UNDERWRITING_RULE`, product `PROPERTY` and text about red rainstorm risk. Assert that ingestion returns a positive chunk count, duplicate IDs return conflict, and a query for `暴雨红色风险如何核保` returns the document with source metadata.

- [ ] **Step 2: Run the tests and verify failure**

Run: `mvn -q -Dtest='KnowledgeServiceTest,KnowledgeSeedLoaderTest' test`

Expected: compilation failure for missing services.

- [ ] **Step 3: Implement service and startup seeding**

`KnowledgeService.ingest` validates IDs, titles, product codes and non-blank content, persists the source document, splits it, embeds chunks and saves them. `KnowledgeSeedLoader` reads four classpath Markdown files and ingests them only when the repository is empty.

- [ ] **Step 4: Run knowledge tests**

Run: `mvn -q -Dtest='KnowledgeServiceTest,KnowledgeSeedLoaderTest' test`

Expected: ingestion, duplicate protection, retrieval and idempotent seeding pass.

- [ ] **Step 5: Commit knowledge ingestion**

```bash
git add src/main/java/com/hrniux/underwriting/rag src/main/resources/knowledge src/test/java/com/hrniux/underwriting/rag
git commit -m "feat: add knowledge ingestion and retrieval"
```

## Task 5: Add versioned prompt templates

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/prompt/PromptTemplateVersion.java`
- Create: `src/main/java/com/hrniux/underwriting/prompt/PromptTemplateRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/prompt/InMemoryPromptTemplateRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/prompt/PromptTemplateService.java`
- Create: `src/main/java/com/hrniux/underwriting/prompt/PromptSeedConfiguration.java`
- Test: `src/test/java/com/hrniux/underwriting/prompt/PromptTemplateServiceTest.java`

- [ ] **Step 1: Write failing strict-rendering tests**

```java
@Test
void rejectsMissingVariables() {
    service.createVersion("underwriting-analysis", "问题：{{question}}\n证据：{{evidence}}",
            Set.of("question", "evidence"));
    assertThatThrownBy(() -> service.preview("underwriting-analysis", Map.of("question", "是否承保")))
            .isInstanceOf(PromptRenderException.class)
            .hasMessageContaining("evidence");
}
```

Also assert monotonically increasing versions, exactly one active version, and immutable history.

- [ ] **Step 2: Run the prompt tests and verify failure**

Run: `mvn -q -Dtest=PromptTemplateServiceTest test`

Expected: compilation failure for missing prompt classes.

- [ ] **Step 3: Implement versioning and `{{variable}}` rendering**

Reject undeclared placeholders and missing declared values. Activating a version deactivates the previous active version atomically within the in-memory repository. Seed version 1 of `underwriting-analysis` with all eight variables from the design.

- [ ] **Step 4: Run prompt tests and commit**

Run: `mvn -q -Dtest=PromptTemplateServiceTest test`

Expected: all prompt tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/prompt src/test/java/com/hrniux/underwriting/prompt
git commit -m "feat: add versioned prompt templates"
```

## Task 6: Implement typed business tools and fake internal systems

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/tool/ToolName.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/ToolCallTrace.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/PolicyFacts.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/QuotationFacts.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/UnderwritingHistoryFacts.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/SurveyReportFacts.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/DisasterRiskFacts.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/UnderwritingFactTools.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/FakeUnderwritingFactTools.java`
- Create: `src/main/java/com/hrniux/underwriting/tool/ToolRegistry.java`
- Test: `src/test/java/com/hrniux/underwriting/tool/FakeUnderwritingFactToolsTest.java`
- Test: `src/test/java/com/hrniux/underwriting/tool/ToolRegistryTest.java`

- [ ] **Step 1: Write failing tool tests**

For `P-1001`, assert warehouse property insurance, CNY 20,000,000 sum insured, two historical claims, unresolved drainage remediation and red rainstorm risk. For `P-2001`, assert a low-risk office scenario. Unknown policy numbers must throw `POLICY_NOT_FOUND`.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -q -Dtest='FakeUnderwritingFactToolsTest,ToolRegistryTest' test`

Expected: missing tool types fail compilation.

- [ ] **Step 3: Implement fake adapters and registry**

Store immutable sample maps in the adapter. `ToolRegistry.invoke` accepts a `ToolName` and policy number, measures elapsed time with `System.nanoTime`, returns the typed result plus a sanitized trace, and records failures before rethrowing the domain exception.

- [ ] **Step 4: Run tool tests and commit**

Run: `mvn -q -Dtest='FakeUnderwritingFactToolsTest,ToolRegistryTest' test`

Expected: sample lookup, unknown policy and trace assertions pass.

```bash
git add src/main/java/com/hrniux/underwriting/tool src/test/java/com/hrniux/underwriting/tool
git commit -m "feat: add underwriting business tools"
```

## Task 7: Implement deterministic underwriting rules

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/rule/Decision.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/RiskLevel.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/RuleSeverity.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/RuleResult.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/UnderwritingContext.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/UnderwritingRule.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/UnderwritingRuleEngine.java`
- Create: `src/main/java/com/hrniux/underwriting/rule/DefaultUnderwritingRules.java`
- Test: `src/test/java/com/hrniux/underwriting/rule/UnderwritingRuleEngineTest.java`

- [ ] **Step 1: Write failing decision-floor tests**

Assert the `P-1001` facts produce `MANUAL_REVIEW`, `HIGH`, a score of at least 70 and hits for rainstorm, claims, high sum insured and unresolved remediation. Assert a critical fire defect plus extreme fire risk produces `REJECT`, and low-risk facts produce `APPROVE` with a score below 40.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -q -Dtest=UnderwritingRuleEngineTest test`

Expected: missing rule types fail compilation.

- [ ] **Step 3: Implement five rules and aggregation**

Start at risk score 10, add each hit's score impact, clamp to 0–100, derive risk level from 0–29, 30–59, 60–79 and 80–100, and choose the strongest decision using `APPROVE < MANUAL_REVIEW < REJECT`. Sort results by rule priority then code.

- [ ] **Step 4: Run rule tests and commit**

Run: `mvn -q -Dtest=UnderwritingRuleEngineTest test`

Expected: all decision-floor and score tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/rule src/test/java/com/hrniux/underwriting/rule
git commit -m "feat: add deterministic underwriting rules"
```

## Task 8: Implement switchable and resilient model gateways

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/model/ModelRequest.java`
- Create: `src/main/java/com/hrniux/underwriting/model/ModelResponse.java`
- Create: `src/main/java/com/hrniux/underwriting/model/ModelGateway.java`
- Create: `src/main/java/com/hrniux/underwriting/model/DeterministicMockModelGateway.java`
- Create: `src/main/java/com/hrniux/underwriting/model/OpenAiCompatibleModelGateway.java`
- Create: `src/main/java/com/hrniux/underwriting/model/RoutingModelGateway.java`
- Create: `src/main/java/com/hrniux/underwriting/model/ModelGatewayConfiguration.java`
- Create: `src/main/java/com/hrniux/underwriting/model/ModelUnavailableException.java`
- Test: `src/test/java/com/hrniux/underwriting/model/DeterministicMockModelGatewayTest.java`
- Test: `src/test/java/com/hrniux/underwriting/model/OpenAiCompatibleModelGatewayTest.java`
- Test: `src/test/java/com/hrniux/underwriting/model/RoutingModelGatewayTest.java`

- [ ] **Step 1: Write failing deterministic and HTTP behavior tests**

Use MockWebServer to assert:

- OpenAI request path is `/v1/chat/completions` and uses a bearer header without exposing it in `toString`.
- HTTP 500 twice followed by 200 yields attempts `3`.
- HTTP 400 is attempted once.
- socket timeout becomes `MODEL_UNAVAILABLE`.
- explicit fallback returns provider `mock` with `fallbackUsed=true`; disabled fallback throws.

- [ ] **Step 2: Run model tests and verify failure**

Run: `mvn -q -Dtest='DeterministicMockModelGatewayTest,OpenAiCompatibleModelGatewayTest,RoutingModelGatewayTest' test`

Expected: missing model gateway types fail compilation.

- [ ] **Step 3: Implement model contract and deterministic provider**

The mock provider maps rule decision, risk level, reasons and evidence into a stable Chinese summary and action list. It reports provider `mock`, configured model name, attempts one and fallback false.

- [ ] **Step 4: Implement OpenAI-compatible HTTP and bounded retry**

Use `RestClient` with a JDK request factory configured from typed durations. Serialize system and user messages to the chat-completions schema. Retry only `IOException`, timeout, 429 and 5xx; wait `retryBackoff` between attempts, preserve thread interruption, and parse `choices[0].message.content`. Never include the API key in exceptions, logs or records.

- [ ] **Step 5: Run model tests and commit**

Run: `mvn -q -Dtest='DeterministicMockModelGatewayTest,OpenAiCompatibleModelGatewayTest,RoutingModelGatewayTest' test`

Expected: deterministic, retry, no-retry, timeout, routing and fallback tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/model src/test/java/com/hrniux/underwriting/model
git commit -m "feat: add resilient model gateway routing"
```

## Task 9: Implement the end-to-end Agent orchestrator

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/agent/AgentStep.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/StepStatus.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/StepTrace.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/EvaluationRequest.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/Evidence.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/UnderwritingEvaluation.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/EvaluationRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/InMemoryEvaluationRepository.java`
- Create: `src/main/java/com/hrniux/underwriting/agent/UnderwritingAgentOrchestrator.java`
- Test: `src/test/java/com/hrniux/underwriting/agent/UnderwritingAgentOrchestratorTest.java`

- [ ] **Step 1: Write failing orchestration tests**

Assert a request for `P-1001`:

- creates or resumes a session;
- executes all seven steps in order with `SUCCESS`;
- records five fact-tool traces plus rule validation;
- includes at least one RAG evidence item;
- returns `MANUAL_REVIEW` or stronger and never below the rule decision;
- appends the user question and assistant summary to the session;
- persists a queryable evaluation.

Also assert missing knowledge raises the decision floor to manual review and a critical tool failure creates a failed step trace before propagating.

- [ ] **Step 2: Run the orchestrator test and verify failure**

Run: `mvn -q -Dtest=UnderwritingAgentOrchestratorTest test`

Expected: missing agent classes fail compilation.

- [ ] **Step 3: Implement the seven-step pipeline**

Use a private `runStep(AgentStep, Supplier<T>)` helper that creates success or failure traces without swallowing exceptions. Collect typed facts, build a retrieval query with question, occupancy and disaster level, execute rules, render the active template, call the router, enforce the deterministic decision floor and save the immutable evaluation.

- [ ] **Step 4: Run orchestrator tests and commit**

Run: `mvn -q -Dtest=UnderwritingAgentOrchestratorTest test`

Expected: all success, evidence-floor and failure-trace tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/agent src/test/java/com/hrniux/underwriting/agent
git commit -m "feat: orchestrate explainable underwriting evaluations"
```

## Task 10: Expose REST APIs and consistent errors

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/api/SessionController.java`
- Create: `src/main/java/com/hrniux/underwriting/api/UnderwritingEvaluationController.java`
- Create: `src/main/java/com/hrniux/underwriting/api/KnowledgeController.java`
- Create: `src/main/java/com/hrniux/underwriting/api/PromptController.java`
- Create: `src/main/java/com/hrniux/underwriting/api/ToolController.java`
- Create: `src/main/java/com/hrniux/underwriting/api/ApiDtos.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/error/DomainException.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/error/ResourceNotFoundException.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/error/ConflictException.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/error/PromptRenderException.java`
- Create: `src/main/java/com/hrniux/underwriting/shared/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/hrniux/underwriting/api/UnderwritingApiIntegrationTest.java`
- Test: `src/test/java/com/hrniux/underwriting/api/ManagementApiIntegrationTest.java`

- [ ] **Step 1: Write failing MockMvc integration tests**

Cover create/get session, evaluate/get evaluation, ingest/list/search knowledge, list/create/activate/preview prompt, list/invoke tools, unknown resource 404 and invalid request 400. Assert each error has `status`, `errorCode`, `traceId` and `timestamp`.

- [ ] **Step 2: Run API tests and verify 404 or missing controller failures**

Run: `mvn -q -Dtest='UnderwritingApiIntegrationTest,ManagementApiIntegrationTest' test`

Expected: endpoint assertions fail before controllers exist.

- [ ] **Step 3: Implement validated controllers and Problem Details mapping**

Use records with `@NotBlank`, `@NotNull`, `@Min` and `@Max`. Return 201 for resource creation, 200 for reads/actions, 400 for validation, 404 for absent resources, 409 for conflicts, 503 for model failures and 500 for unexpected failures. Generate or propagate `X-Trace-Id` and return it in the error extension fields.

- [ ] **Step 4: Run API tests and commit**

Run: `mvn -q -Dtest='UnderwritingApiIntegrationTest,ManagementApiIntegrationTest' test`

Expected: all endpoint and error-contract tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/api src/main/java/com/hrniux/underwriting/shared/error src/test/java/com/hrniux/underwriting/api
git commit -m "feat: expose underwriting management APIs"
```

## Task 11: Register and verify six MCP tools

**Files:**
- Create: `src/main/java/com/hrniux/underwriting/tool/mcp/UnderwritingMcpTools.java`
- Test: `src/test/java/com/hrniux/underwriting/tool/mcp/UnderwritingMcpToolsTest.java`
- Test: `src/test/java/com/hrniux/underwriting/tool/mcp/McpRegistrationIntegrationTest.java`

- [ ] **Step 1: Write failing annotation and registration tests**

Reflect over `UnderwritingMcpTools` and assert exactly these six names:

```text
get_policy
get_quotation
get_underwriting_history
get_survey_report
get_disaster_risk
validate_rules
```

Assert every method is annotated with `@McpTool`, every input has `@McpToolParam(required = true)`, each tool returns the same typed data as `ToolRegistry`, and the application exposes the Streamable MCP endpoint.

- [ ] **Step 2: Run MCP tests and verify failure**

Run: `mvn -q -Dtest='UnderwritingMcpToolsTest,McpRegistrationIntegrationTest' test`

Expected: missing MCP tool class or registration assertions fail.

- [ ] **Step 3: Implement annotated delegating methods**

Annotate a Spring service method per tool using `@McpTool(name = "get_policy", description = "查询财险保单与标的信息", generateOutputSchema = true)` and the corresponding names/descriptions for the other five. Each method accepts one required `policyNo`, delegates to the typed registry or rule engine and contains no duplicate business data.

- [ ] **Step 4: Run MCP tests and commit**

Run: `mvn -q -Dtest='UnderwritingMcpToolsTest,McpRegistrationIntegrationTest' test`

Expected: six annotations, delegation and Streamable endpoint tests pass.

```bash
git add src/main/java/com/hrniux/underwriting/tool/mcp src/test/java/com/hrniux/underwriting/tool/mcp src/main/resources/application.yml
git commit -m "feat: expose underwriting tools over MCP"
```

## Task 12: Add documentation, demo script and container delivery

**Files:**
- Create: `README.md`
- Create: `docs/ARCHITECTURE.md`
- Create: `docs/API_EXAMPLES.md`
- Create: `docs/INTERVIEW_GUIDE.md`
- Create: `scripts/demo.sh`
- Create: `Dockerfile`
- Create: `.dockerignore`
- Test: `src/test/java/com/hrniux/underwriting/docs/DocumentationContractTest.java`

- [ ] **Step 1: Write a failing documentation contract test**

Assert the four documents, demo script and Dockerfile exist; README contains Java 21, no-key startup, model switching, RAG, MCP, sample policy numbers and a production evolution section; API examples mention every public endpoint group; interview guide contains a five-minute flow and at least ten common questions.

- [ ] **Step 2: Run documentation test and verify failure**

Run: `mvn -q -Dtest=DocumentationContractTest test`

Expected: assertions fail for missing deliverables.

- [ ] **Step 3: Write the operator and interview documentation**

README commands must be executable:

```bash
mvn clean verify
mvn spring-boot:run
curl http://localhost:8080/actuator/health
bash scripts/demo.sh
```

Document `P-1001` high-risk and `P-2001` low-risk scenarios, the seven orchestration steps, the exact OpenAI-compatible environment variables, Streamable MCP URL discovery, and replacements for Redis, PostgreSQL/PGVector, enterprise model gateway and real internal APIs.

- [ ] **Step 4: Add a deterministic demo script and Dockerfile**

`scripts/demo.sh` uses `set -euo pipefail`, waits for health with a bounded loop, runs knowledge search, tool invocation and evaluation, and prints JSON through `python3 -m json.tool`. The Dockerfile uses a Maven build stage and Java 21 runtime, runs as a non-root user and includes a health check.

- [ ] **Step 5: Run documentation test and commit**

Run: `mvn -q -Dtest=DocumentationContractTest test`

Expected: documentation contract passes.

```bash
chmod +x scripts/demo.sh
git add README.md docs scripts Dockerfile .dockerignore src/test/java/com/hrniux/underwriting/docs
git commit -m "docs: add demo and interview guide"
```

## Task 13: Complete verification, review, GitHub creation and push

**Files:**
- Modify only files required by evidence-backed verification failures.

- [ ] **Step 1: Run focused and full automated verification**

```bash
mvn test
mvn clean verify
git diff --check
git status --short
```

Expected: all tests pass, build succeeds, no whitespace errors and no uncommitted implementation files.

- [ ] **Step 2: Run the packaged JAR and API/MCP smoke checks**

Start `java -jar target/insurance-underwriting-agent-demo-*.jar`, wait for health, run `scripts/demo.sh`, request `/v3/api-docs`, load `/swagger-ui/index.html`, initialize the Streamable MCP endpoint and list/call tools. Stop the process cleanly.

Expected: health `UP`, both sample evaluations succeed, OpenAPI is valid JSON, Swagger HTML loads, MCP lists exactly six tools and a tool call returns sample data.

- [ ] **Step 3: Build and smoke-test Docker**

```bash
docker build -t insurance-underwriting-agent-demo:local .
docker run --rm -d --name underwriting-agent-demo -p 18080:8080 insurance-underwriting-agent-demo:local
curl --fail http://localhost:18080/actuator/health
docker stop underwriting-agent-demo
```

Expected: image builds, container health endpoint returns `UP`, and the container stops cleanly.

- [ ] **Step 4: Perform requirement-by-requirement code review**

Audit every design acceptance criterion against current source, tests, runtime output and documentation. Run secret scans for `api-key`, bearer tokens and private keys; confirm only environment placeholders exist. Fix only verified issues, add regression tests first and rerun the affected suite.

- [ ] **Step 5: Commit final verified adjustments**

```bash
git add -A
git commit -m "chore: finalize verified underwriting demo"
```

Skip the commit only if `git status --porcelain` is empty.

- [ ] **Step 6: Create the public GitHub repository without exposing credentials**

Obtain the existing GitHub HTTPS credential through `git credential fill` inside a script that never prints the password, call `POST https://api.github.com/user/repos` with name `insurance-underwriting-agent-demo`, description `Explainable Java property-insurance underwriting Agent demo with RAG, rules and MCP tools`, `private=false`, and handle HTTP 422 by verifying that the existing repository belongs to `hrniux` before proceeding.

- [ ] **Step 7: Push and prove remote consistency**

```bash
git remote add origin https://github.com/hrniux/insurance-underwriting-agent-demo.git
git push -u origin main
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git ls-remote origin refs/heads/main
```

Expected: working tree clean; local `HEAD`, `origin/main` and remote `refs/heads/main` have the identical full commit SHA.

## Plan self-review record

- Every requirement in design sections 5–15 maps to Tasks 2–12.
- Every acceptance criterion in design section 17 maps to Tasks 1–13.
- REST and MCP reuse the same tool registry; no duplicate tool business logic is planned.
- The model decision cannot lower the deterministic rule decision floor.
- Default configuration starts without external services or secrets.
- The plan contains no deferred implementation markers or undecided dependencies.
