package com.hrniux.underwriting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UnderwritingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(UnderwritingAgentApplication.class, args);
    }
}
