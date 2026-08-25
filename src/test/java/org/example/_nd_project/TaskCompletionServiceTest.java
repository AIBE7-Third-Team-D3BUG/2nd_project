package org.example._nd_project;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.Review;
import org.example._nd_project.submission.ReviewForm;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.submission.TaskCompletionService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCompletionServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock DisputeRepository disputeRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock TimeLedgerService timeLedgerService;
    @Mock MemberRepository memberRepository;

    private TaskCompletionService completionService;

    @BeforeEach
    void setUp() {
        completionService = new TaskCompletionService(
                taskRepository,
                submissionRepository,
                disputeRepository,
                reviewRepository,
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

        ReviewForm form = new ReviewForm();
        form.setRating(5);
        form.setContent("빠르게 해결해주셨어요.");
        form.setDeadlineMet(true);

        completionService.approve(10L, 3L, form);

        verify(timeLedgerService).settleTask(3L, 8L, 10L, 120);
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertEquals(10L, reviewCaptor.getValue().getTaskId());
        assertEquals(3L, reviewCaptor.getValue().getReviewerId());
        assertEquals(8L, reviewCaptor.getValue().getRevieweeId());
        assertEquals(5, reviewCaptor.getValue().getRating());
        assertEquals("빠르게 해결해주셨어요.", reviewCaptor.getValue().getContent());
        assertEquals(true, reviewCaptor.getValue().getDeadlineMet());
        verify(task).complete(org.mockito.ArgumentMatchers.eq(3L), any(Instant.class));
        verify(memberRepository).recordCompletedTaskReview(8L, 5);
    }

    @Test
    void duplicateReviewDoesNotSettleTaskAgain() {
        Task task = mock(Task.class);
        when(task.isRequester(3L)).thenReturn(true);
        when(task.getStatus()).thenReturn(TaskStatus.SUBMITTED);
        when(task.getWorkerId()).thenReturn(8L);
        when(taskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(task));
        when(submissionRepository.findByTaskId(10L)).thenReturn(Optional.of(mock(Submission.class)));
        when(reviewRepository.existsByTaskId(10L)).thenReturn(true);

        ReviewForm form = new ReviewForm();
        form.setRating(5);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> completionService.approve(10L, 3L, form)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(timeLedgerService, never()).settleTask(anyLong(), anyLong(), anyLong(), anyInt());
        verify(memberRepository, never()).recordCompletedTaskReview(anyLong(), anyInt());
    }
}
