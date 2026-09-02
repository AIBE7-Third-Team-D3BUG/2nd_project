package org.example._nd_project.submission;

import org.example._nd_project.notification.DelayPenaltyNotificationService;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.example._nd_project.task.TaskStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;

@Service
public class SubmissionService {

    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final TaskStorageService taskStorageService;
    private final SubmissionDeadlinePolicy submissionDeadlinePolicy;
    private final DelayPenaltyNotificationService delayPenaltyNotificationService;

    public SubmissionService(TaskRepository taskRepository,
                             SubmissionRepository submissionRepository,
                             TaskStorageService taskStorageService,
                             SubmissionDeadlinePolicy submissionDeadlinePolicy,
                             DelayPenaltyNotificationService delayPenaltyNotificationService) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.taskStorageService = taskStorageService;
        this.submissionDeadlinePolicy = submissionDeadlinePolicy;
        this.delayPenaltyNotificationService = delayPenaltyNotificationService;
    }

    @Transactional
    public void submit(Long taskId, Long workerId, SubmissionForm form, MultipartFile resultFile) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isWorker(workerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 업무만 결과를 제출할 수 있습니다.");
        }

        Submission submission = submissionRepository.findByTaskId(taskId).orElse(null);
        String previousAsset = submission == null ? null : submission.getResultFileUrl();
        String uploadedPath = null;
        try {
            if (resultFile != null && !resultFile.isEmpty()) {
                uploadedPath = taskStorageService.upload(taskId, resultFile);
            }
            String resultLink = normalizeNullable(form.getResultLink());
            String nextAsset = uploadedPath != null
                    ? uploadedPath
                    : resultLink != null ? resultLink : previousAsset;
            String description = form.getResultDescription().trim();
            Instant submittedAt = Instant.now();

            boolean firstSubmission = submission == null;
            if (firstSubmission) {
                SubmissionDeadlineAssessment deadlineAssessment = submissionDeadlinePolicy
                        .assessAtSubmission(task, submittedAt);
                submission = Submission.create(
                        taskId,
                        workerId,
                        description,
                        nextAsset,
                        task.getRequestedMinutes(),
                        deadlineAssessment,
                        submittedAt
                );
            } else {
                submission.resubmit(description, nextAsset, task.getRequestedMinutes());
            }
            submissionRepository.saveAndFlush(submission);
            task.submitResult(workerId, submittedAt);
            taskRepository.flush();
            if (firstSubmission && submission.getDeadlineAssessment().overdue()) {
                delayPenaltyNotificationService.notifyFirstDelay(submission);
            }

            if (isStoredObject(previousAsset) && !previousAsset.equals(nextAsset)) {
                taskStorageService.deleteQuietly(previousAsset);
            }
        } catch (RuntimeException exception) {
            taskStorageService.deleteQuietly(uploadedPath);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public URI createResultDownloadUrl(Long taskId, Long memberId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isParticipant(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String resultAsset = submissionRepository.findByTaskId(taskId)
                .map(Submission::getResultFileUrl)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return isExternalUrl(resultAsset)
                ? URI.create(resultAsset)
                : taskStorageService.createSignedDownloadUrl(resultAsset);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isExternalUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private static boolean isStoredObject(String value) {
        return value != null && !value.isBlank() && !isExternalUrl(value);
    }
}
