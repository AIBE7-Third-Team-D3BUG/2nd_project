package org.example._nd_project.task;

import org.example._nd_project.chat.ChatService;
import org.example._nd_project.member.TimeLedgerService;
import org.example._nd_project.submission.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class TaskService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final TaskRepository taskRepository;
    private final TaskStorageService taskStorageService;
    private final TimeLedgerService timeLedgerService;
    private final SubmissionRepository submissionRepository;
    private final ChatService chatService;

    public TaskService(TaskRepository taskRepository, TaskStorageService taskStorageService,
                       TimeLedgerService timeLedgerService,
                       SubmissionRepository submissionRepository,
                       ChatService chatService) {
        this.taskRepository = taskRepository;
        this.taskStorageService = taskStorageService;
        this.timeLedgerService = timeLedgerService;
        this.submissionRepository = submissionRepository;
        this.chatService = chatService;
    }

    @Transactional
    public TaskListItem create(Long requesterId, TaskCreateForm form, MultipartFile attachment) {
        Task task = Task.create(
                requesterId,
                form.getTitle().trim(),
                form.getDescription().trim(),
                form.getCategory(),
                form.normalizedSkillTags(),
                form.getRequestedMinutes(),
                form.getDeadlineAt().atZone(KOREA).toInstant(),
                form.getDeliverableDescription().trim(),
                normalizeNullable(form.getReferenceFileUrl())
        );
        taskRepository.saveAndFlush(task);
        timeLedgerService.reserveForTask(requesterId, task.getId(), form.getRequestedMinutes());
        String uploadedPath = null;
        try {
            if (attachment != null && !attachment.isEmpty()) {
                uploadedPath = taskStorageService.upload(task.getId(), attachment);
                task.attachReferenceFile(uploadedPath);
                taskRepository.flush();
            }
        } catch (RuntimeException exception) {
            taskStorageService.deleteQuietly(uploadedPath);
            throw exception;
        }
        return toListItem(task);
    }

    @Transactional(readOnly = true)
    public TaskEditData getEditData(Long taskId, Long requesterId) {
        Task task = findOwnedTask(taskId, requesterId);
        ensureEditable(task);

        TaskCreateForm form = new TaskCreateForm();
        form.setTitle(task.getTitle());
        form.setDescription(task.getDescription());
        form.setCategory(task.getCategory());
        form.setSkillTags(String.join(", ", task.getRequiredSkillTags()));
        form.setRequestedMinutes(task.getRequestedMinutes());
        form.setDeadlineAt(task.getDeadlineAt().atZone(KOREA).toLocalDateTime());
        form.setDeliverableDescription(task.getDeliverableDescription());
        if (isExternalUrl(task.getReferenceFileUrl())) {
            form.setReferenceFileUrl(task.getReferenceFileUrl());
        }

        return new TaskEditData(
                form,
                task.getCreatedAt().plus(Duration.ofHours(24)).atZone(KOREA).toLocalDateTime(),
                hasReference(task.getReferenceFileUrl()),
                timeLedgerService.getAvailableMinutes(requesterId)
                        + timeLedgerService.getTaskReservedMinutes(requesterId, taskId)
        );
    }

    @Transactional
    public TaskListItem update(Long taskId, Long requesterId, TaskCreateForm form, MultipartFile attachment) {
        Task task = findOwnedTask(taskId, requesterId);
        ensureEditable(task);

        Instant deadline = form.getDeadlineAt().atZone(KOREA).toInstant();
        if (deadline.isAfter(task.getCreatedAt().plus(Duration.ofHours(24)))) {
            throw new ResponseStatusException(CONFLICT, "마감은 최초 등록 시점부터 24시간 이내여야 합니다.");
        }

        String previousReference = task.getReferenceFileUrl();
        String uploadedPath = null;
        try {
            if (attachment != null && !attachment.isEmpty()) {
                uploadedPath = taskStorageService.upload(task.getId(), attachment);
            }
            String requestedLink = normalizeNullable(form.getReferenceFileUrl());
            String nextReference = uploadedPath != null
                    ? uploadedPath
                    : requestedLink != null
                        ? requestedLink
                        : isStoredObject(previousReference) ? previousReference : null;

            task.updateDetails(
                    form.getTitle().trim(),
                    form.getDescription().trim(),
                    form.getCategory(),
                    form.normalizedSkillTags(),
                    form.getRequestedMinutes(),
                    deadline,
                    form.getDeliverableDescription().trim(),
                    nextReference
            );
            timeLedgerService.adjustTaskReservation(
                    requesterId,
                    taskId,
                    form.getRequestedMinutes()
            );
            taskRepository.flush();

            if (isStoredObject(previousReference) && !previousReference.equals(nextReference)) {
                taskStorageService.deleteQuietly(previousReference);
            }
            return toListItem(task);
        } catch (RuntimeException exception) {
            taskStorageService.deleteQuietly(uploadedPath);
            throw exception;
        }
    }

    @Transactional
    public void delete(Long taskId, Long requesterId) {
        Task task = findOwnedTask(taskId, requesterId);
        ensureEditable(task);
        String reference = task.getReferenceFileUrl();
        timeLedgerService.refundTaskReservation(requesterId, taskId);
        task.cancel(Instant.now());
        taskRepository.flush();
        if (isStoredObject(reference)) {
            taskStorageService.deleteQuietly(reference);
        }
    }

    @Transactional
    public void cancelActiveTask(Long taskId, Long requesterId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (!Objects.equals(task.getRequesterId(), requesterId)) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        if (task.getStatus() != TaskStatus.MATCHED && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ResponseStatusException(CONFLICT, "매칭 또는 진행 중인 업무만 중도 취소할 수 있습니다.");
        }

        String taskReference = task.getReferenceFileUrl();
        String resultAsset = submissionRepository.findByTaskId(taskId)
                .map(submission -> {
                    submissionRepository.delete(submission);
                    return submission.getResultFileUrl();
                })
                .orElse(null);
        timeLedgerService.refundTaskReservation(
                requesterId,
                taskId,
                "의뢰자의 업무 중도 취소에 따른 예약 재화 반환"
        );
        submissionRepository.flush();
        chatService.deleteRoomForTask(taskId);
        taskRepository.delete(task);
        taskRepository.flush();
        if (isStoredObject(taskReference)) {
            taskStorageService.deleteQuietly(taskReference);
        }
        if (isStoredObject(resultAsset)) {
            taskStorageService.deleteQuietly(resultAsset);
        }
    }

    @Transactional
    public int expireOverdueOpenTasks() {
        Instant now = Instant.now();
        List<Task> overdueTasks = taskRepository.findByStatusAndDeadlineAtBefore(TaskStatus.OPEN, now);
        for (Task task : overdueTasks) {
            expireTask(task, now);
        }
        return overdueTasks.size();
    }

    private void expireTask(Task task, Instant now) {
        String reference = task.getReferenceFileUrl();
        timeLedgerService.refundTaskReservation(task.getRequesterId(), task.getId(), "모집 기한 만료에 따른 예약 재화 반환");
        task.cancel(now);
        taskRepository.flush();
        if (isStoredObject(reference)) {
            taskStorageService.deleteQuietly(reference);
        }
    }

    @Transactional
    public List<TaskListItem> findOpenTasks(TaskSort taskSort, TaskCategory category) {
        expireOverdueOpenTasks();
        Instant now = Instant.now();
        PageRequest page = PageRequest.of(0, 20, taskSort.getSort());
        List<Task> tasks = category == null
                ? taskRepository.findByStatusAndDeadlineAtAfter(TaskStatus.OPEN, now, page)
                : taskRepository.findByStatusAndCategoryAndDeadlineAtAfter(TaskStatus.OPEN, category, now, page);
        return tasks
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional
    public List<TaskListItem> findRegisteredTasks(Long requesterId) {
        expireOverdueOpenTasks();
        return taskRepository.findByRequesterIdAndStatusNotOrderByCreatedAtDesc(requesterId, TaskStatus.CANCELLED)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskListItem> findAssignedTasks(Long workerId) {
        return taskRepository.findByWorkerIdAndStatusNotOrderByUpdatedAtDesc(workerId, TaskStatus.CANCELLED)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskListItem> findWorkingTasks(Long workerId) {
        return taskRepository.findByWorkerIdAndStatusInOrderByUpdatedAtDesc(workerId, ACTIVE_STATUSES)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(
            TaskStatus.MATCHED, TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED, TaskStatus.DISPUTED
    );

    @Transactional(readOnly = true)
    public boolean hasActiveTask(Long memberId) {
        if (memberId == null) {
            return false;
        }
        return taskRepository.existsActiveTaskByMemberId(memberId, ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLatestActiveTaskId(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        List<Task> activeTasks = taskRepository.findActiveTasksByMemberId(
                memberId, ACTIVE_STATUSES, PageRequest.of(0, 1)
        );
        return activeTasks.isEmpty() ? Optional.empty() : Optional.of(activeTasks.get(0).getId());
    }

    @Transactional(readOnly = true)
    public Optional<TaskListItem> findTaskById(Long taskId) {
        return taskRepository.findById(taskId).map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(Long taskId, Long requesterId) {
        Task task = taskRepository.findByIdAndRequesterId(taskId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        String reference = task.getReferenceFileUrl();
        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        if (reference.startsWith("https://") || reference.startsWith("http://")) {
            return URI.create(reference);
        }
        return taskStorageService.createSignedDownloadUrl(reference);
    }

    private TaskListItem toListItem(Task task) {
        Duration remaining = Duration.between(Instant.now(), task.getDeadlineAt());
        boolean urgent = !remaining.isNegative() && remaining.compareTo(Duration.ofHours(3)) <= 0;
        return new TaskListItem(
                task.getId(),
                task.getRequesterId(),
                task.getTitle(),
                task.getDescription(),
                task.getCategory().getLabel(),
                Arrays.asList(task.getRequiredSkillTags()),
                task.getRequestedMinutes(),
                task.getDeadlineAt(),
                formatDeadline(task.getDeadlineAt(), remaining),
                task.getStatus().getLabel(),
                task.getStatus() == TaskStatus.OPEN,
                task.getStatus() != TaskStatus.OPEN && task.getStatus() != TaskStatus.CANCELLED,
                urgent,
                task.getReferenceFileUrl() != null && !task.getReferenceFileUrl().isBlank(),
                task.getDeliverableDescription()
        );
    }

    private String formatDeadline(Instant deadline, Duration remaining) {
        if (remaining.isNegative()) {
            return "마감";
        }
        long minutes = remaining.toMinutes();
        if (minutes < 60) {
            return minutes + "분 남음";
        }
        if (minutes < 24 * 60) {
            long hours = minutes / 60;
            long restMinutes = minutes % 60;
            return restMinutes == 0 ? hours + "시간 남음" : hours + "시간 " + restMinutes + "분 남음";
        }
        return DEADLINE_FORMAT.format(deadline.atZone(KOREA));
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Task findOwnedTask(Long taskId, Long requesterId) {
        return taskRepository.findByIdAndRequesterId(taskId, requesterId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private void ensureEditable(Task task) {
        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(CONFLICT, "모집 중인 업무만 수정하거나 삭제할 수 있습니다.");
        }
    }

    private static boolean hasReference(String reference) {
        return reference != null && !reference.isBlank();
    }

    private static boolean isExternalUrl(String reference) {
        return reference != null && (reference.startsWith("https://") || reference.startsWith("http://"));
    }

    private static boolean isStoredObject(String reference) {
        return hasReference(reference) && !isExternalUrl(reference);
    }
}
