package org.example._nd_project;

import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskProgressDomainTest {

    @Test
    void assignedWorkerCanSubmitAndRequesterCanApprove() {
        Task task = inProgressTask();
        Instant submittedAt = Instant.parse("2026-08-24T05:00:00Z");
        Instant completedAt = Instant.parse("2026-08-24T06:00:00Z");

        task.submitResult(8L, submittedAt);
        assertEquals(TaskStatus.SUBMITTED, task.getStatus());
        assertEquals(submittedAt, task.getSubmittedAt());

        task.complete(3L, completedAt);
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertEquals(completedAt, task.getCompletedAt());
    }

    @Test
    void requesterCanReturnSubmittedResultToWorker() {
        Task task = inProgressTask();
        task.submitResult(8L, Instant.now());

        task.requestRevision(3L);

        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
    }

    @Test
    void memberWhoIsNotAssignedCannotSubmit() {
        Task task = inProgressTask();

        assertThrows(IllegalArgumentException.class, () -> task.submitResult(99L, Instant.now()));
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
    }

    @Test
    void selectedWorkerCanStartMatchedTask() {
        Task task = matchedTask();
        Instant startedAt = Instant.parse("2026-08-24T04:30:00Z");

        task.startWork(8L, startedAt);

        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(startedAt, task.getStartedAt());
    }

    @Test
    void memberWhoWasNotSelectedCannotStartTask() {
        Task task = matchedTask();

        assertThrows(IllegalArgumentException.class, () -> task.startWork(99L, Instant.now()));
        assertEquals(TaskStatus.MATCHED, task.getStatus());
    }

    private Task inProgressTask() {
        Task task = matchedTask();
        ReflectionTestUtils.setField(task, "status", TaskStatus.IN_PROGRESS);
        return task;
    }

    private Task matchedTask() {
        Task task = Task.create(
                3L,
                "AWS 배포 후 502 오류 해결",
                "배포 문제 해결",
                TaskCategory.DEVELOPMENT,
                new String[]{"AWS", "Nginx"},
                120,
                Instant.now().plusSeconds(7_200),
                "서비스 정상 응답 확인",
                null
        );
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "workerId", 8L);
        ReflectionTestUtils.setField(task, "status", TaskStatus.MATCHED);
        return task;
    }
}
