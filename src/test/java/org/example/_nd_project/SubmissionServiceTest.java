package org.example._nd_project;

import org.example._nd_project.notification.DelayPenaltyNotificationService;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionDeadlineAssessment;
import org.example._nd_project.submission.SubmissionDeadlinePolicy;
import org.example._nd_project.submission.SubmissionForm;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.SubmissionService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.example._nd_project.task.TaskStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock TaskStorageService taskStorageService;
    @Mock DelayPenaltyNotificationService delayPenaltyNotificationService;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(
                taskRepository,
                submissionRepository,
                taskStorageService,
                new SubmissionDeadlinePolicy(),
                delayPenaltyNotificationService
        );
    }

    @Test
    void assignedWorkerSubmitsResultFromInProgressTask() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.isWorker(8L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(task.getRequestedMinutes()).thenReturn(120);
        when(task.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(3_600));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        SubmissionForm form = new SubmissionForm();
        form.setResultDescription("Nginx upstream 설정을 수정하고 정상 응답을 확인했습니다.");
        form.setResultLink("https://example.com/result");

        submissionService.submit(10L, 8L, form, null);

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).saveAndFlush(captor.capture());
        assertEquals("Nginx upstream 설정을 수정하고 정상 응답을 확인했습니다.",
                captor.getValue().getResultDescription());
        assertEquals("https://example.com/result", captor.getValue().getResultFileUrl());
        assertEquals(120, captor.getValue().getActualMinutes());
        assertEquals(SubmissionDeadlineAssessment.Status.ON_TIME,
                captor.getValue().getDeadlineAssessment().status());
        verify(task).submitResult(org.mockito.ArgumentMatchers.eq(8L), any(Instant.class));
        verify(taskRepository).flush();
    }

    @Test
    void lateFirstSubmissionStoresImmutableDelayAssessment() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.isWorker(8L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(task.getRequestedMinutes()).thenReturn(120);
        when(task.getDeadlineAt()).thenReturn(Instant.now().minusSeconds(20 * 60));
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.empty());
        SubmissionForm form = new SubmissionForm();
        form.setResultDescription("지연 제출 결과");

        submissionService.submit(10L, 8L, form, null);

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).saveAndFlush(captor.capture());
        SubmissionDeadlineAssessment assessment = captor.getValue().getDeadlineAssessment();
        assertEquals(SubmissionDeadlineAssessment.Status.LATE, assessment.status());
        assertTrue(assessment.lateMinutes() >= 20 && assessment.lateMinutes() <= 21);
        assertEquals(60, assessment.severeThresholdMinutes());
        verify(delayPenaltyNotificationService).notifyFirstDelay(captor.getValue());
    }

    @Test
    void resubmissionKeepsFirstSubmissionAssessment() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.isWorker(8L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(task.getRequestedMinutes()).thenReturn(120);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        SubmissionDeadlineAssessment firstAssessment = new SubmissionDeadlineAssessment(
                SubmissionDeadlineAssessment.Status.LATE,
                true,
                25,
                60
        );
        Submission existing = Submission.create(
                10L, 8L, "최초 결과", null, 120, firstAssessment, Instant.now().minusSeconds(300)
        );
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.of(existing));
        SubmissionForm form = new SubmissionForm();
        form.setResultDescription("수정 결과");

        submissionService.submit(10L, 8L, form, null);

        assertEquals("수정 결과", existing.getResultDescription());
        assertEquals(SubmissionDeadlineAssessment.Status.LATE, existing.getDeadlineAssessment().status());
        assertEquals(25, existing.getDeadlineAssessment().lateMinutes());
        verify(delayPenaltyNotificationService, never()).notifyFirstDelay(any());
    }

    @Test
    void unassignedMemberCannotSubmitResult() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.isWorker(99L)).thenReturn(false);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        SubmissionForm form = new SubmissionForm();
        form.setResultDescription("결과");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> submissionService.submit(10L, 99L, form, null)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(submissionRepository, never()).saveAndFlush(any());
    }
}
