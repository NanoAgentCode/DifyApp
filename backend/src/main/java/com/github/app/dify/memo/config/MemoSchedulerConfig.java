package com.github.app.dify.memo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 备忘录定时提醒专用调度器：独立单线程，与全局 @Scheduled 线程池隔离。
 */
@Configuration
public class MemoSchedulerConfig {

    public static final String MEMO_REMINDER_SCHEDULER = "memoReminderScheduler";

    @Bean(name = MEMO_REMINDER_SCHEDULER)
    public TaskScheduler memoReminderScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("memo-reminder-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }
}
