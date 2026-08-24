package org.example._nd_project.volunteer;

import java.util.List;

public record AppliedTaskItem(
        Long volunteerId,
        Long taskId,
        String title,
        String description,
        String categoryLabel,
        List<String> skillTags,
        int requestedPum,
        String deadlineLabel,
        String taskStatusLabel,
        VolunteerStatus volunteerStatus,
        String volunteerStatusLabel,
        String appliedDateLabel,
        boolean hasAttachment
) {
}