package com.github.app.dify.observability.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 可观测性监控配置
 * 提供专门的线程池用于异步监控任务
 */
@Configuration
@EnableAsync
public class ObservabilityConfig {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityConfig.class);

    /**
     * 是否启用监控（默认启用）
     */
    @Value("${observability.enabled:true}")
    private boolean enabled = true;

    /**
     * 监控线程池核心线程数（默认4）
     */
    @Value("${observability.thread-pool.core-size:4}")
    private int corePoolSize = 4;

    /**
     * 监控线程池最大线程数（默认8）
     */
    @Value("${observability.thread-pool.max-size:8}")
    private int maxPoolSize = 8;

    /**
     * 监控线程池队列容量（默认500）
     */
    @Value("${observability.thread-pool.queue-capacity:500}")
    private int queueCapacity = 500;

    /**
     * 监控专用线程池
     * 用于异步执行监控相关的数据库操作，避免阻塞主业务流程
     */
    @Bean(name = "observabilityExecutor")
    public Executor observabilityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Observability-");
        
        // 拒绝策略：丢弃最老的任务（监控数据可以丢失，不能影响业务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        
        // 等待所有任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        logger.info("监控线程池已配置 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}, 启用状态: {}", 
                corePoolSize, maxPoolSize, queueCapacity, enabled);
        
        return executor;
    }

    /**
     * 是否启用监控
     */
    public boolean isEnabled() {
        return enabled;
    }
}
