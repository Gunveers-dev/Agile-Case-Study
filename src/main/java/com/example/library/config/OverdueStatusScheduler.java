package com.example.library.config;

import com.example.library.service.CirculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the stored loan status in step with the calendar: ISSUED loans past their due date become OVERDUE.
 * Runs once when the application starts and then every night at 00:05.
 */
@Component
public class OverdueStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueStatusScheduler.class);

    private final CirculationService circulationService;

    public OverdueStatusScheduler(CirculationService circulationService) {
        this.circulationService = circulationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        refresh();
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void nightly() {
        refresh();
    }

    private void refresh() {
        int updated = circulationService.refreshOverdueStatuses();
        if (updated > 0) {
            log.info("Marked {} loan(s) as OVERDUE", updated);
        }
    }
}
