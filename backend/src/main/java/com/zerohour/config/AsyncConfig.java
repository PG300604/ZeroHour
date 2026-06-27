package com.zerohour.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for agent pipeline execution.
 * Provides the thread pool used by @Async("sseTaskExecutor") in AgentOrchestrator.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("agent-pipeline-");
        executor.initialize();
        return executor;
    }
}
