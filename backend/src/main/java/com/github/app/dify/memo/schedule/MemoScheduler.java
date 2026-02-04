package com.github.app.dify.memo.schedule;

import com.github.app.dify.memo.config.MemoSchedulerConfig;
import com.github.app.dify.memo.domain.Memo;
import com.github.app.dify.memo.repository.MemoRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * 备忘录定时提醒：由后端独立线程（memo-reminder）周期性扫描到期项，并自动标记为已提醒。
 * 前端轮询 GET /api/memos/due 仍可获取到期待办并弹浏览器通知；若用户未打开页面，后端也会在到期后自动标记。
 */
@Component
public class MemoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MemoScheduler.class);
    private static final long PERIOD_MS = 60_000L;
    /** 宽限时间（毫秒）：到期后超过此时间才由后端自动标记，以便前端有机会轮询并弹窗 */
    private static final long GRACE_MS = 2 * 60 * 1000L;

    @Autowired
    private MemoRepository memoRepository;

    @Autowired
    @Qualifier(MemoSchedulerConfig.MEMO_REMINDER_SCHEDULER)
    private TaskScheduler taskScheduler;

    private volatile ScheduledFuture<?> future;

    @PostConstruct
    public void start() {
        if (taskScheduler == null) {
            logger.warn("未注入 memoReminderScheduler，备忘录定时提醒未启动");
            return;
        }
        future = taskScheduler.scheduleAtFixedRate(this::tick, Duration.ofMillis(PERIOD_MS));
        logger.info("备忘录定时提醒线程已启动，扫描间隔 {} 秒", PERIOD_MS / 1000);
    }

    @PreDestroy
    public void stop() {
        if (future != null) {
            future.cancel(false);
            logger.debug("备忘录定时提醒已停止");
        }
    }

    public void tick() {
        try {
            Date now = new Date();
            Date before = new Date(now.getTime() - GRACE_MS);
            List<Memo> due = memoRepository.findAllDueBefore(before);
            for (Memo m : due) {
                try {
                    if (m.getIntervalMinutes() != null && m.getIntervalMinutes() > 0) {
                        Date nextRemindAt = addMinutes(m.getRemindAt(), m.getIntervalMinutes());
                        int updated = memoRepository.updateRemindAtById(m.getId(), nextRemindAt);
                        if (updated > 0) {
                            logger.debug("备忘录周期已推进下次提醒: id={}, content={}, next={}", m.getId(), m.getContent(),
                                    nextRemindAt);
                        }
                    } else {
                        int updated = memoRepository.markDoneById(m.getId());
                        if (updated > 0) {
                            logger.debug("备忘录已自动标记已提醒: id={}, content={}", m.getId(), m.getContent());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("备忘录自动处理失败: id={}", m.getId(), e);
                }
            }
        } catch (Exception e) {
            logger.warn("备忘录定时扫描异常", e);
        }
    }

    private static Date addMinutes(Date date, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }
}
