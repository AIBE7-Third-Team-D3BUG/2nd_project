package org.example._nd_project.volunteer;

import org.example._nd_project.chat.ChatService;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class VolunteerService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter APPLIED_DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final VolunteerRepository volunteerRepository;
    private final TaskRepository taskRepository;
    private final MemberRepository memberRepository;
    private final ChatService chatService;

    public VolunteerService(VolunteerRepository volunteerRepository,
                            TaskRepository taskRepository,
                            MemberRepository memberRepository,
                            ChatService chatService) {
        this.volunteerRepository = volunteerRepository;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.chatService = chatService;
    }

    @Transactional
    public Volunteer apply(Long taskId, Long memberId, String message) {
        Task task = findTaskForUpdate(taskId);

        if (task.getStatus() != TaskStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모집 중인 업무에만 지원할 수 있습니다.");
        }

        if (task.getRequesterId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인이 등록한 업무에는 지원할 수 없습니다.");
        }

        Volunteer existing = volunteerRepository.findByTaskIdAndMemberId(taskId, memberId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == VolunteerStatus.APPLIED || existing.getStatus() == VolunteerStatus.ACCEPTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 지원한 업무입니다.");
            }
            existing.resetToApplied();
            return volunteerRepository.save(existing);
        }

        Volunteer volunteer = Volunteer.create(taskId, memberId, message == null ? "" : message.trim());
        return volunteerRepository.save(volunteer);
    }

    @Transactional
    public void cancelApplication(Long taskId, Long memberId) {
        Task task = findTaskForUpdate(taskId);
        Volunteer volunteer = volunteerRepository.findByTaskIdAndMemberId(taskId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원 정보를 찾을 수 없습니다."));

        if (volunteer.getStatus() == VolunteerStatus.ACCEPTED) {
            if (task.getWorkerId() != null && task.getWorkerId().equals(memberId)) {
                task.unassignWorker();
                chatService.deleteRoomForTask(taskId);
            }
            List<Volunteer> remaining = volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(taskId, VolunteerStatus.CANCELLED);
            for (Volunteer v : remaining) {
                if (v != volunteer && (volunteer.getId() == null || !volunteer.getId().equals(v.getId()))) {
                    v.resetToApplied();
                }
            }
        }

        volunteerRepository.delete(volunteer);
    }

    @Transactional(readOnly = true)
    public boolean hasApplied(Long taskId, Long memberId) {
        if (taskId == null || memberId == null) {
            return false;
        }
        return volunteerRepository.existsByTaskIdAndMemberIdAndStatusIn(taskId, memberId, List.of(VolunteerStatus.APPLIED, VolunteerStatus.ACCEPTED));
    }

    @Transactional(readOnly = true)
    public long countApplicants(Long taskId) {
        if (taskId == null) {
            return 0;
        }
        return volunteerRepository.countByTaskIdAndStatus(taskId, VolunteerStatus.APPLIED);
    }

    @Transactional(readOnly = true)
    public List<VolunteerCardView> getVolunteers(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<Volunteer> volunteers = volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(taskId, VolunteerStatus.CANCELLED);
        return volunteers.stream()
                .map(this::toCardView)
                .toList();
    }

    @Transactional
    public void selectVolunteer(Long taskId, Long requesterId, Long volunteerId) {
        Task task = findTaskForUpdate(taskId);

        if (!task.getRequesterId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "의뢰인만 지원자를 선택할 수 있습니다.");
        }

        Volunteer selected = volunteerRepository.findByIdAndTaskId(volunteerId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원자를 찾을 수 없습니다."));

        List<Volunteer> allVolunteers = volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(taskId, VolunteerStatus.CANCELLED);
        for (Volunteer v : allVolunteers) {
            if (v.getId().equals(selected.getId())) {
                v.accept();
            } else {
                v.reject();
            }
        }

        task.assignWorker(selected.getMemberId(), Instant.now());
        chatService.ensureRoomForTask(task);
    }

    @Transactional
    public void unselectVolunteer(Long taskId, Long requesterId, Long volunteerId) {
        Task task = findTaskForUpdate(taskId);

        if (!task.getRequesterId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "의뢰인만 작업자 선택을 취소할 수 있습니다.");
        }

        Volunteer selected = volunteerRepository.findByIdAndTaskId(volunteerId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지원자를 찾을 수 없습니다."));

        if (selected.getStatus() != VolunteerStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택된 작업자만 선택을 취소할 수 있습니다.");
        }

        task.unassignWorker();
        chatService.deleteRoomForTask(taskId);

        List<Volunteer> allVolunteers = volunteerRepository.findByTaskIdAndStatusNotOrderByCreatedAtAsc(taskId, VolunteerStatus.CANCELLED);
        for (Volunteer v : allVolunteers) {
            v.resetToApplied();
        }
    }

    private Task findTaskForUpdate(Long taskId) {
        return taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<AppliedTaskItem> findAppliedTasks(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        List<Volunteer> applications = volunteerRepository.findByMemberIdAndStatusOrderByCreatedAtDesc(memberId, VolunteerStatus.APPLIED);
        List<AppliedTaskItem> result = new java.util.ArrayList<>();
        for (Volunteer v : applications) {
            Task task = taskRepository.findById(v.getTaskId()).orElse(null);
            if (task == null || task.getStatus() == TaskStatus.CANCELLED) {
                continue;
            }
            Instant appliedAt = v.getCreatedAt() != null ? v.getCreatedAt() : Instant.now();
            String appliedDateLabel = APPLIED_DATE_FORMAT.format(appliedAt.atZone(KOREA));
            VolunteerStatus status = v.getStatus() != null ? v.getStatus() : VolunteerStatus.APPLIED;
            String deadlineLabel = formatDeadline(task.getDeadlineAt());
            result.add(new AppliedTaskItem(
                    v.getId(),
                    v.getTaskId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getCategory().getLabel(),
                    task.getRequiredSkillTags() != null ? Arrays.asList(task.getRequiredSkillTags()) : List.of(),
                    task.getRequestedMinutes() / 30,
                    deadlineLabel,
                    task.getStatus().getLabel(),
                    status,
                    status.getLabel(),
                    appliedDateLabel,
                    (task.getReferenceLinkUrl() != null && !task.getReferenceLinkUrl().isBlank())
                            || (task.getAttachmentObjectPath() != null && !task.getAttachmentObjectPath().isBlank())
            ));
        }
        return result;
    }

    private String formatDeadline(Instant deadline) {
        if (deadline == null) return "마감일 미정";
        java.time.Duration remaining = java.time.Duration.between(Instant.now(), deadline);
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
        return APPLIED_DATE_FORMAT.format(deadline.atZone(KOREA));
    }

    private VolunteerCardView toCardView(Volunteer volunteer) {
        Member member = memberRepository.findById(volunteer.getMemberId()).orElse(null);
        String nickname = member != null ? member.getNickname() : "사용자";
        String avatarText = nickname.length() >= 2 ? nickname.substring(nickname.length() - 2) : nickname;
        int completedCount = member != null ? member.getCompletedTaskCount() : 0;
        int reviewCount = member != null ? member.getReviewCount() : 0;
        int ratingSum = member != null ? member.getRatingSum() : 0;
        double rating = reviewCount > 0 ? (double) ratingSum / reviewCount : 5.0;
        String ratingText = String.format(Locale.KOREA, "★ %.1f", rating);
        VolunteerStatus status = volunteer.getStatus() != null ? volunteer.getStatus() : VolunteerStatus.APPLIED;
        String statusLabel = status.getLabel();
        List<String> skillTags = (member != null && member.getSkillTags() != null) ? Arrays.asList(member.getSkillTags()) : List.of();
        Instant appliedAt = volunteer.getCreatedAt() != null ? volunteer.getCreatedAt() : Instant.now();
        String appliedDateLabel = APPLIED_DATE_FORMAT.format(appliedAt.atZone(KOREA));

        return new VolunteerCardView(
                volunteer.getId(),
                volunteer.getMemberId(),
                nickname,
                avatarText,
                completedCount,
                ratingText,
                skillTags,
                volunteer.getMessage(),
                status,
                statusLabel,
                appliedAt,
                appliedDateLabel
        );
    }
}
