package org.example._nd_project.task;

import java.time.Instant;
import java.util.List;

public record TaskListItem(
        Long id,
        Long requesterId,
        String title,
        String description,
        String categoryLabel,
        List<String> skillTags,
        int requestedMinutes,
        Instant deadlineAt,
        String deadlineLabel,
        String statusLabel,
        boolean editable,
        boolean progressAvailable,
        boolean urgent,
        boolean hasAttachment,
        String deliverableDescription
) {
    public int requestedPum() {
        return requestedMinutes / 30;
    }
}
