package com.eightbit.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Even though the puzzle drops at midnight, we push at a civilised hour (build doc section 10).
 * 8:00 AM IST: "Today's 8Bit puzzle is live."
 */
@Component
public class DailyReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DailyReminderJob.class);

    private final PushService push;

    public DailyReminderJob(PushService push) {
        this.push = push;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    public void sendDailyReminder() {
        if (!push.isEnabled()) return;
        int sent = push.broadcast("8Bit Daily Puzzle", "Today's puzzle is live. Keep your streak alive! 🔥", "/play");
        log.info("Daily reminder pushed to {} subscribers", sent);
    }
}
