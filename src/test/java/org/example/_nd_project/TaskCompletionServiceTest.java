package org.example._nd_project;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCompletionServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock DisputeRepository disputeRepository;
    @Mock TimeLedgerService timeLedgerService;
    @Mock MemberRepository memberRepository;

    private TaskCompletionService completionService;

    @BeforeEach
    void setUp() {
        completionService = new TaskCompletionService(
                taskRepository,
                submissionRepository,
                disputeRepository,
                timeLedgerService,
                memberRepository
        );
    }

    @Test
    void requesterApprovalSettlesReservedTimeAndCompletesTask() {
        Task task = mock(Task.class);
        when(task.isRequester(3L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.SUBMITTED);
        when(task.getRequesterId()).thenReturn(3L);
        when(task.getWorkerId()).thenReturn(8L);
        when(task.getId()).thenReturn(10L);
        when(task.getRequestedMinutes()).thenReturn(120);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.of(mock(Submission.class)));

        completionService.approve(10L, 3L);

        verify(timeLedgerService).settleTask(3L, 8L, 10L, 120);
        verify(task).complete(org.mockito.ArgumentMatchers.eq(3L), any(Instant.class));
        verify(memberRepository).incrementCompletedTaskCount(8L);
    }
}
