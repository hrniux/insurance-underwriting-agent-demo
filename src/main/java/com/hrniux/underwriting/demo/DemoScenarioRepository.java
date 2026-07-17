package com.hrniux.underwriting.demo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

@Component
public class DemoScenarioRepository {

    static final String DATA_RESOURCE = "demo/underwriting-scenarios.json";

    private final Map<String, DemoScenario> scenarios;

    public DemoScenarioRepository(ObjectMapper mapper) {
        this(mapper, new ClassPathResource(DATA_RESOURCE));
    }

    DemoScenarioRepository(ObjectMapper mapper, Resource resource) {
        this(read(mapper, resource));
    }

    DemoScenarioRepository(List<DemoScenario> scenarios) {
        this.scenarios = validateAndIndex(scenarios);
    }

    public static DemoScenarioRepository loadDefault() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        return new DemoScenarioRepository(mapper, new ClassPathResource(DATA_RESOURCE));
    }

    public List<DemoScenario> findAll() {
        return List.copyOf(scenarios.values());
    }

    public DemoScenario required(String policyNo) {
        DemoScenario scenario = scenarios.get(policyNo);
        if (scenario == null) {
            throw new ResourceNotFoundException("POLICY_NOT_FOUND", policyNo);
        }
        return scenario;
    }

    private static List<DemoScenario> read(ObjectMapper mapper, Resource resource) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        try (var input = resource.getInputStream()) {
            return mapper.readValue(input, new TypeReference<>() {});
        }
        catch (IOException | RuntimeException error) {
            throw new IllegalStateException("无法读取演示场景资源：" + resource.getDescription(), error);
        }
    }

    private static Map<String, DemoScenario> validateAndIndex(List<DemoScenario> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("演示场景列表不能为空");
        }

        Map<String, DemoScenario> index = new LinkedHashMap<>();
        values.stream()
                .sorted(java.util.Comparator.comparing(DemoScenario::policyNo,
                        java.util.Comparator.nullsFirst(String::compareTo)))
                .forEach(scenario -> {
                    validate(scenario);
                    DemoScenario existing = index.putIfAbsent(scenario.policyNo(), scenario);
                    if (existing != null) {
                        throw invalid(scenario.policyNo(), "存在重复保单号");
                    }
                });
        return Collections.unmodifiableMap(index);
    }

    private static void validate(DemoScenario scenario) {
        if (scenario == null) {
            throw new IllegalStateException("演示场景不能为 null");
        }
        String policyNo = requireText(scenario.policyNo(), "UNKNOWN", "policyNo");
        requireText(scenario.name(), policyNo, "name");
        requireText(scenario.summary(), policyNo, "summary");
        requireText(scenario.question(), policyNo, "question");
        if (scenario.learningPoints() == null || scenario.learningPoints().isEmpty()) {
            throw invalid(policyNo, "learningPoints 不能为空");
        }

        var expected = require(scenario.expectedResult(), policyNo, "expectedResult");
        require(expected.decision(), policyNo, "expectedResult.decision");
        require(expected.riskLevel(), policyNo, "expectedResult.riskLevel");
        if (expected.riskScore() < 0 || expected.riskScore() > 100) {
            throw invalid(policyNo, "预期风险分必须在 0 到 100 之间");
        }
        if (expected.ruleCodes().stream().distinct().count() != expected.ruleCodes().size()) {
            throw invalid(policyNo, "预期规则代码不能重复");
        }

        var policy = require(scenario.policy(), policyNo, "policy");
        var quotation = require(scenario.quotation(), policyNo, "quotation");
        var history = require(scenario.history(), policyNo, "history");
        var survey = require(scenario.survey(), policyNo, "survey");
        var disaster = require(scenario.disaster(), policyNo, "disaster");
        ensurePolicyNo(policyNo, policy.policyNo(), "policy");
        ensurePolicyNo(policyNo, quotation.policyNo(), "quotation");
        ensurePolicyNo(policyNo, history.policyNo(), "history");
        ensurePolicyNo(policyNo, survey.policyNo(), "survey");
        ensurePolicyNo(policyNo, disaster.policyNo(), "disaster");

        if (!policy.endDate().isAfter(policy.startDate())) {
            throw invalid(policyNo, "保险终期必须晚于保险起期");
        }
        nonNegative(policyNo, "sumInsured", quotation.sumInsured());
        nonNegative(policyNo, "rate", quotation.rate());
        nonNegative(policyNo, "premium", quotation.premium());
        nonNegative(policyNo, "deductible", quotation.deductible());
        nonNegative(policyNo, "paidLossThreeYears", history.paidLossThreeYears());
        if (history.claimCountThreeYears() < 0) {
            throw invalid(policyNo, "claimCountThreeYears 不能为负数");
        }
    }

    private static String requireText(String value, String policyNo, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(policyNo, field + " 不能为空");
        }
        return value;
    }

    private static <T> T require(T value, String policyNo, String field) {
        if (value == null) {
            throw invalid(policyNo, field + " 不能为空");
        }
        return value;
    }

    private static void ensurePolicyNo(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw invalid(expected, field + " 的保单号不一致：" + actual);
        }
    }

    private static void nonNegative(String policyNo, String field, BigDecimal value) {
        if (value.signum() < 0) {
            throw invalid(policyNo, field + " 不能为负数");
        }
    }

    private static IllegalStateException invalid(String policyNo, String message) {
        return new IllegalStateException("演示场景 " + policyNo + " 无效：" + message);
    }
}
