package org.example._nd_project;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.SubmissionDeadlinePolicy;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.TaskProgressService;
import org.example._nd_project.submission.TaskProgressView;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskCategory;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskProgressServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock MemberRepository memberRepository;

    private TaskProgressService service;

    @BeforeEach
    void setUp() {
        service = new TaskProgressService(
                taskRepository,
                submissionRepository,
                reviewRepository,
                memberRepository,
                new SubmissionDeadlinePolicy()
        );
    }

    @Test
    void completedTaskShowsPersistedFirstSubmissionAssessment() {
        Instant submittedAt = Instant.now().minusSeconds(600);
        Task task = Task.create(
                3L,
                "지연 이력 확인",
                "완료된 업무",
                TaskCategory.DEVELOPMENT,
                new String[0],
                120,
                submittedAt.minusSeconds(60 * 60),
                "결과 제출",
                null
        );
        ReflectionTestUtils.setField(task, "id", 10L);
        ReflectionTestUtils.setField(task, "workerId", 8L);
        ReflectionTestUtils.setField(task, "status", TaskStatus.COMPLETED);
        ReflectionTestUtils.setField(task, "submittedAt", submittedAt);
        ReflectionTestUtils.setField(task, "completedAt", submittedAt.plusSeconds(300));
        ReflectionTestUtils.setField(task, "createdAt", submittedAt.minusSeconds(7_200));
        Submission submission = Submission.create(
                10L,
                8L,
                "완료 결과",
                null,
                120,
                new SubmissionDeadlineAssessment(
                        SubmissionDeadlineAssessment.Status.SEVERE,
                        true,
                        60,
                        60
                ),
                submittedAt
        );
        ReflectionTestUtils.setField(submission, "createdAt", submittedAt);
        ReflectionTestUtils.setField(submission, "updatedAt", submittedAt);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.of(submission));
        when(reviewRepository.findByTaskId(10L)).thenReturn(Optional.empty());

        TaskProgressView result = service.getProgress(10L, 3L);

        assertTrue(result.deadlineStatus().visible());
        assertTrue(result.deadlineStatus().severe());
        assertEquals("심각한 제출 지연 · 60분 지연", result.deadlineStatus().label());
    }
}
