package org.example._nd_project.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskExpirationScheduler.class);

    private final TaskService taskService;

    public TaskExpirationScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedRate = 60000)
    public void run() {
        try {
            int expiredCount = taskService.expireOverdueOpenTasks();
            if (expiredCount > 0) {
                log.info("Expired {} overdue open tasks and refunded reservations.", expiredCount);
            }
        } catch (Exception exception) {
            log.error("Failed to expire overdue open tasks", exception);
        }
    }
}
