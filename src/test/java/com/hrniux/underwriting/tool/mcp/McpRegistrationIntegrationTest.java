package com.hrniux.underwriting.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration.ServerMcpAnnotatedBeans;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class McpRegistrationIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private McpServerStreamableHttpProperties properties;

    @Autowired
    private ServerMcpAnnotatedBeans annotatedBeans;

    @Test
    void registersAllSixToolsAndTheStreamableHttpTransport() {
        Set<String> names = SyncMcpAnnotationProviders
                .toolSpecifications(annotatedBeans.getBeansByAnnotation(McpTool.class)).stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder(
                "get_policy",
                "get_quotation",
                "get_underwriting_history",
                "get_survey_report",
                "get_disaster_risk",
                "validate_rules");
        assertThat(context.getBeansOfType(WebMvcStreamableServerTransportProvider.class)).hasSize(1);
        assertThat(properties.getMcpEndpoint()).isEqualTo("/mcp");
    }
}
