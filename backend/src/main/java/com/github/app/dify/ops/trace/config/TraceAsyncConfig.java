package com.github.app.dify.ops.trace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Trace 模块异步线程池配置。
 */
@Configuration
public class TraceAsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(TraceAsyncConfig.class);

    @Value("${trace.async.core-size:2}")
    private int corePoolSize = 2;

    @Value("${trace.async.max-size:8}")
    private int maxPoolSize = 8;

    @Value("${trace.async.queue-capacity:1000}")
    private int queueCapacity = 1000;

    @Bean(name = "traceExecutor")
    public Executor traceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Trace-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        logger.info("Trace线程池已初始化 - core: {}, max: {}, queue: {}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}

