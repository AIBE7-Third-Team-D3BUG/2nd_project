package org.example._nd_project.submission;

import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TaskProgressService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final MemberRepository memberRepository;

    public TaskProgressService(TaskRepository taskRepository,
                               SubmissionRepository submissionRepository,
                               MemberRepository memberRepository) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public TaskProgressView getProgress(Long taskId, Long memberId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!task.isParticipant(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Submission submission = submissionRepository.findByTaskId(taskId).orElse(null);
        String requesterName = memberRepository.findById(task.getRequesterId())
                .map(member -> member.getNickname())
                .orElse("의뢰인");
        String workerName = task.getWorkerId() == null
                ? "매칭 대기 중"
                : memberRepository.findById(task.getWorkerId())
                    .map(member -> member.getNickname())
                    .orElse("작업자");

        return new TaskProgressView(
                task.getId(),
                task.getTitle(),
                requesterName,
                workerName,
                formatDeadline(task),
                task.getRequestedMinutes(),
                task.getStatus().getLabel(),
                task.isRequester(memberId),
                task.isWorker(memberId),
                task.isWorker(memberId) && task.getStatus() == TaskStatus.IN_PROGRESS,
                task.isRequester(memberId) && task.getStatus() == TaskStatus.SUBMITTED,
                task.getStatus() == TaskStatus.COMPLETED,
                task.getStatus() == TaskStatus.DISPUTED,
                currentStep(task.getStatus()),
                completionCriteria(task),
                activities(task, requesterName, workerName),
                toSubmissionView(submission, task)
        );
    }

    private List<String> completionCriteria(Task task) {
        List<String> criteria = Arrays.stream(task.getDeliverableDescription().split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        return criteria.isEmpty() ? List.of(task.getDeliverableDescription()) : criteria;
    }

    private List<TaskProgressView.ActivityView> activities(Task task, String requesterName, String workerName) {
        List<TaskProgressView.ActivityView> activities = new ArrayList<>();
        addActivity(activities, "업무가 등록되었습니다.", requesterName, task.getCreatedAt(), false);
        addActivity(activities, "작업자가 매칭되었습니다.", workerName, task.getMatchedAt(), false);
        addActivity(activities, "작업이 시작되었습니다.", workerName, task.getStartedAt(), false);
        addActivity(activities, "결과가 제출되었습니다.", workerName, task.getSubmittedAt(),
                task.getStatus() == TaskStatus.SUBMITTED);
        addActivity(activities, "완료 승인 및 정산이 완료되었습니다.", requesterName, task.getCompletedAt(),
                task.getStatus() == TaskStatus.COMPLETED);
        return activities;
    }

    private void addActivity(List<TaskProgressView.ActivityView> activities, String title,
                             String description, Instant occurredAt, boolean current) {
        if (occurredAt != null) {
            activities.add(new TaskProgressView.ActivityView(
                    title,
                    description,
                    DATE_TIME.format(occurredAt.atZone(KOREA)),
                    current
            ));
        }
    }

    private TaskProgressView.SubmissionView toSubmissionView(Submission submission, Task task) {
        if (submission == null) {
            return null;
        }
        boolean hasAsset = submission.getResultFileUrl() != null
                && !submission.getResultFileUrl().isBlank();
        return new TaskProgressView.SubmissionView(
                submission.getResultDescription(),
                hasAsset,
                isExternalUrl(submission.getResultFileUrl()) ? "결과 링크 열기" : "제출 파일 열기",
                formatInstant(task.getSubmittedAt() != null ? task.getSubmittedAt() : submission.getUpdatedAt()),
                submission.getRequesterNote()
        );
    }

    private String formatDeadline(Task task) {
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return "완료";
        }
        Duration remaining = Duration.between(Instant.now(), task.getDeadlineAt());
        if (remaining.isNegative()) {
            return "마감 지남 · " + DATE_TIME.format(task.getDeadlineAt().atZone(KOREA));
        }
        long minutes = remaining.toMinutes();
        if (minutes < 60) {
            return minutes + "분 남음";
        }
        if (minutes < 24 * 60) {
            return (minutes / 60) + "시간 " + (minutes % 60) + "분 남음";
        }
        return DATE_TIME.format(task.getDeadlineAt().atZone(KOREA));
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(KOREA));
    }

    private int currentStep(TaskStatus status) {
        return switch (status) {
            case OPEN -> 1;
            case MATCHED -> 2;
            case IN_PROGRESS -> 3;
            case SUBMITTED, DISPUTED -> 4;
            case COMPLETED, CANCELLED -> 5;
        };
    }

    private boolean isExternalUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
