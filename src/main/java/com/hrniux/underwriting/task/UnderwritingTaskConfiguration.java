package com.hrniux.underwriting.task;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.hrniux.underwriting.shared.config.UnderwritingTaskProperties;

@Configuration(proxyBeanMethods = false)
public class UnderwritingTaskConfiguration {

    public static final String EXECUTOR_BEAN = "underwritingTaskExecutor";

    @Bean(name = EXECUTOR_BEAN)
    ThreadPoolTaskExecutor underwritingTaskExecutor(UnderwritingTaskProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("underwriting-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
