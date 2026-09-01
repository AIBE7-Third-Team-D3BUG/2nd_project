package org.example._nd_project.submission;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class TaskCompletionService {

    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final DisputeRepository disputeRepository;
    private final ReviewRepository reviewRepository;
    private final TimeLedgerService timeLedgerService;
    private final MemberRepository memberRepository;

    public TaskCompletionService(TaskRepository taskRepository,
                                 SubmissionRepository submissionRepository,
                                 DisputeRepository disputeRepository,
                                 ReviewRepository reviewRepository,
                                 TimeLedgerService timeLedgerService,
                                 MemberRepository memberRepository) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.disputeRepository = disputeRepository;
        this.reviewRepository = reviewRepository;
        this.timeLedgerService = timeLedgerService;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void approve(Long taskId, Long requesterId, ReviewForm form) {
        Task task = findLockedRequesterTask(taskId, requesterId);
        ensureSubmitted(task);
        if (task.getWorkerId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "매칭된 작업자를 찾을 수 없습니다.");
        }
        Submission submission = submissionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "제출된 결과를 찾을 수 없습니다."
                ));
        if (reviewRepository.existsByTaskId(taskId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 후기가 작성된 업무입니다.");
        }
        boolean deadlineMet = submission.getDeadlineAssessment().deadlineMet();
        try {
            timeLedgerService.settleTask(
                    task.getRequesterId(),
                    task.getWorkerId(),
                    task.getId(),
                    task.getRequestedMinutes()
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        reviewRepository.save(Review.create(
                task.getId(),
                requesterId,
                task.getWorkerId(),
                form.getRating(),
                form.getContent(),
                deadlineMet
        ));
        task.complete(requesterId, Instant.now());
        memberRepository.recordCompletedTaskReview(task.getWorkerId(), form.getRating());
    }

    @Transactional
    public void requestRevision(Long taskId, Long requesterId, RevisionRequestForm form) {
        Task task = findLockedRequesterTask(taskId, requesterId);
        ensureSubmitted(task);
        Submission submission = submissionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "제출된 결과를 찾을 수 없습니다."
                ));
        submission.recordRevisionRequest(form.getRequesterNote().trim());
        task.requestRevision(requesterId);
    }

    @Transactional
    public void openDispute(Long taskId, Long memberId, DisputeForm form) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isParticipant(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ensureSubmitted(task);
        if (disputeRepository.existsByTaskId(taskId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고된 업무입니다.");
        }
        disputeRepository.save(Dispute.open(taskId, memberId, form.getDescription().trim()));
        task.openDispute(memberId);
    }

    private Task findLockedRequesterTask(Long taskId, Long requesterId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isRequester(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private void ensureSubmitted(Task task) {
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결과 확인 중인 업무만 처리할 수 있습니다.");
        }
    }
}
