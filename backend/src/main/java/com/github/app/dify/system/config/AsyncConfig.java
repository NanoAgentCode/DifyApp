package com.github.app.dify.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * 
 * 修复线程池配置缺失问题：
 * - 避免使用默认的 SimpleAsyncTaskExecutor（为每个任务创建新线程）
 * - 配置合理的线程池参数，防止线程数爆炸
 * - 提供统一的异步任务执行器
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * 获取CPU核心数
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * 核心线程数 = CPU核心数
     */
    private static final int CORE_POOL_SIZE = CPU_COUNT;

    /**
     * 最大线程数 = CPU核心数 * 2
     */
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2;

    /**
     * 队列容量
     */
    private static final int QUEUE_CAPACITY = 200;

    /**
     * 线程空闲时间（秒）
     */
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * 线程名称前缀
     */
    private static final String THREAD_NAME_PREFIX = "Async-";

    /**
     * 默认异步执行器（用于 @Async 注解）
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = createThreadPoolTaskExecutor("Default");
        logger.info("默认异步线程池已配置 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        return executor;
    }

    /**
     * applicationTaskExecutor Bean（供其他地方使用，如 DataAnalysisServiceImpl）
     */
    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = createThreadPoolTaskExecutor("Application");
        logger.info("applicationTaskExecutor Bean 已创建 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        return executor;
    }

    /**
     * 创建线程池任务执行器
     */
    private ThreadPoolTaskExecutor createThreadPoolTaskExecutor(String name) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数 = CPU核心数
        executor.setCorePoolSize(CORE_POOL_SIZE);

        // 最大线程数 = CPU核心数 * 2
        executor.setMaxPoolSize(MAX_POOL_SIZE);

        // 队列容量
        executor.setQueueCapacity(QUEUE_CAPACITY);

        // 线程空闲时间
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);

        // 线程名称前缀
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX + name + "-");

        // 拒绝策略：由调用线程执行（避免任务丢失）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 初始化
        executor.initialize();

        return executor;
    }

    /**
     * 异步异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            logger.error("异步任务执行失败 - 方法: {}, 参数: {}, 异常: {}", 
                    method.getName(), 
                    params != null ? Arrays.toString(params) : "null",
                    throwable.getMessage(), 
                    throwable);
        };
    }
}
