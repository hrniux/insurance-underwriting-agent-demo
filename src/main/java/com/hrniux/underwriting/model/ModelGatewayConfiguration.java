package com.hrniux.underwriting.model;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hrniux.underwriting.shared.config.AgentProperties;

@Configuration
public class ModelGatewayConfiguration {

    @Bean
    public ModelGateway modelGateway(AgentProperties properties) {
        AgentProperties.Model config = properties.model();
        DeterministicMockModelGateway mock = new DeterministicMockModelGateway(config.model());
        if ("mock".equalsIgnoreCase(config.provider())) {
            return new RoutingModelGateway(mock, mock, false);
        }
        ModelGateway compatible = new OpenAiCompatibleModelGateway(
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                config.connectTimeout(),
                config.readTimeout(),
                config.maxAttempts(),
                config.retryBackoff());
        return new RoutingModelGateway(compatible, mock, config.fallbackToMock());
    }
}
