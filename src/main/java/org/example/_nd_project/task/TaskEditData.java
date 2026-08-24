package org.example._nd_project.task;

import java.time.LocalDateTime;

public record TaskEditData(
        TaskCreateForm form,
        LocalDateTime maximumDeadline,
        boolean hasAttachment,
        int maximumSpendableMinutes
) {
}
