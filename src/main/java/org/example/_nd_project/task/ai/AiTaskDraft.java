package org.example._nd_project.task.ai;

import org.example._nd_project.task.TaskCategory;

import java.util.List;

public record AiTaskDraft(
        String title,
        TaskCategory category,
        List<String> requiredSkills,
        String deliverableDescription,
        String detailDescription
) {
}
