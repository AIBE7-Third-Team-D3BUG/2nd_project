package org.example._nd_project.task.ai;

import org.example._nd_project.task.TaskCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskAiService {

    private static final Logger log = LoggerFactory.getLogger(TaskAiService.class);
    private static final int MAX_SITUATION_LENGTH = 3000;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_DELIVERABLE_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 3000;
    private static final int MAX_SKILL_COUNT = 10;
    private static final int MAX_SKILL_LENGTH = 50;

    private final TaskAiClient taskAiClient;

    public TaskAiService(TaskAiClient taskAiClient) {
        this.taskAiClient = taskAiClient;
    }

    public AiTaskDraft createDraft(String situation) {
        String normalizedSituation = requireText(situation, "상황 설명", MAX_SITUATION_LENGTH);
        try {
            return validateAndNormalize(taskAiClient.generateDraft(normalizedSituation));
        } catch (TaskAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI task draft generation failed: {}", exception.getClass().getSimpleName());
            throw new TaskAiException("AI가 업무를 정리하지 못했습니다. 잠시 후 다시 시도하거나 직접 입력해주세요.", exception);
        }
    }

    private AiTaskDraft validateAndNormalize(AiTaskDraft draft) {
        if (draft == null) {
            throw new TaskAiException("AI가 빈 결과를 반환했습니다. 다시 시도해주세요.");
        }

        String title = requireAiText(draft.title(), "업무 제목", MAX_TITLE_LENGTH);
        TaskCategory category = draft.category();
        if (category == null) {
            throw new TaskAiException("AI가 카테고리를 정하지 못했습니다. 다시 시도해주세요.");
        }
        String deliverable = requireAiText(
                draft.deliverableDescription(), "완료 기준", MAX_DELIVERABLE_LENGTH
        );
        String description = requireAiText(
                draft.detailDescription(), "상세 설명", MAX_DESCRIPTION_LENGTH
        );

        List<String> skills = draft.requiredSkills() == null
                ? List.of()
                : draft.requiredSkills().stream()
                    .filter(skill -> skill != null && !skill.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        if (skills.size() > MAX_SKILL_COUNT
                || skills.stream().anyMatch(skill -> skill.length() > MAX_SKILL_LENGTH)) {
            throw new TaskAiException("AI가 생성한 기술 태그 형식이 올바르지 않습니다. 다시 시도해주세요.");
        }

        return new AiTaskDraft(title, category, skills, deliverable, description);
    }

    private String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "을 입력해주세요.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자 이내로 입력해주세요.");
        }
        return normalized;
    }

    private String requireAiText(String value, String fieldName, int maxLength) {
        try {
            return requireText(value, fieldName, maxLength);
        } catch (IllegalArgumentException exception) {
            throw new TaskAiException("AI가 생성한 " + fieldName + " 형식이 올바르지 않습니다. 다시 시도해주세요.");
        }
    }
}
