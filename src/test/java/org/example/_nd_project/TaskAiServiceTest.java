package org.example._nd_project;

import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.ai.AiTaskDraft;
import org.example._nd_project.task.ai.TaskAiClient;
import org.example._nd_project.task.ai.TaskAiException;
import org.example._nd_project.task.ai.TaskAiService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskAiServiceTest {

    @Test
    void createsValidatedDraftWithoutPersistingAnything() {
        AtomicInteger calls = new AtomicInteger();
        TaskAiClient client = situation -> {
            calls.incrementAndGet();
            return new AiTaskDraft(
                    "  AWS 배포 후 502 오류 해결  ",
                    TaskCategory.DEVELOPMENT,
                    List.of("AWS", "Nginx", "AWS", " Spring Boot "),
                    " 서비스 URL 정상 접속 확인\n502 오류 원인과 해결 내용 기록 ",
                    " 배포 환경과 로그를 확인하고 오류 원인을 분석합니다. "
            );
        };
        TaskAiService service = new TaskAiService(client);

        AiTaskDraft draft = service.createDraft("오늘 AWS에 배포했는데 502 오류가 발생합니다.");

        assertEquals(1, calls.get());
        assertEquals("AWS 배포 후 502 오류 해결", draft.title());
        assertEquals(TaskCategory.DEVELOPMENT, draft.category());
        assertEquals(List.of("AWS", "Nginx", "Spring Boot"), draft.requiredSkills());
        assertEquals("서비스 URL 정상 접속 확인\n502 오류 원인과 해결 내용 기록", draft.deliverableDescription());
    }

    @Test
    void rejectsBlankSituationBeforeCallingAi() {
        AtomicInteger calls = new AtomicInteger();
        TaskAiService service = new TaskAiService(situation -> {
            calls.incrementAndGet();
            return null;
        });

        assertThrows(IllegalArgumentException.class, () -> service.createDraft("   "));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsDraftThatViolatesTaskFieldLimits() {
        TaskAiService service = new TaskAiService(situation -> new AiTaskDraft(
                "업무",
                TaskCategory.DEVELOPMENT,
                List.of("x".repeat(51)),
                "완료 기준",
                "상세 설명"
        ));

        assertThrows(TaskAiException.class, () -> service.createDraft("상황 설명"));
    }
}
