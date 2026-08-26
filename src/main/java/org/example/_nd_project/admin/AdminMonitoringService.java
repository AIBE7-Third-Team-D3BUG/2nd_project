package org.example._nd_project.admin;

import org.example._nd_project.chat.ChatMessage;
import org.example._nd_project.chat.ChatMessageRepository;
import org.example._nd_project.chat.ChatRoom;
import org.example._nd_project.chat.ChatRoomRepository;
import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.member.MemberRole;
import org.example._nd_project.member.MemberStatus;
import org.example._nd_project.submission.Dispute;
import org.example._nd_project.submission.DisputeRepository;
import org.example._nd_project.submission.Review;
import org.example._nd_project.submission.ReviewRepository;
import org.example._nd_project.submission.Submission;
import org.example._nd_project.submission.SubmissionRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskRepository;
import org.example._nd_project.task.TaskStorageService;
import org.example._nd_project.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@Service
public class AdminMonitoringService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final String BLINDED_PREVIEW = "관리자에 의해 블라인드된 메시지입니다.";

    private final MemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SubmissionRepository submissionRepository;
    private final ReviewRepository reviewRepository;
    private final DisputeRepository disputeRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final TaskStorageService taskStorageService;

    public AdminMonitoringService(MemberRepository memberRepository, TaskRepository taskRepository,
                                  ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository,
                                  SubmissionRepository submissionRepository, ReviewRepository reviewRepository,
                                  DisputeRepository disputeRepository, AdminAuditLogRepository auditLogRepository,
                                  TaskStorageService taskStorageService) {
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.submissionRepository = submissionRepository;
        this.reviewRepository = reviewRepository;
        this.disputeRepository = disputeRepository;
        this.auditLogRepository = auditLogRepository;
        this.taskStorageService = taskStorageService;
    }

    @Transactional(readOnly = true)
    public AdminChatListView getChatRooms() {
        return getChatRooms(null, null);
    }

    @Transactional(readOnly = true)
    public AdminChatListView getChatRooms(String query) {
        return getChatRooms(query, null);
    }

    @Transactional(readOnly = true)
    public AdminChatListView getChatRooms(String query, LocalDate date) {
        String normalizedQuery = query == null ? "" : query.trim();
        List<ChatRoom> rooms;
        if (date != null) {
            List<Long> memberIds = normalizedQuery.isBlank()
                    ? List.of() : memberRepository.findIdsByNicknameOrEmail(normalizedQuery);
            Set<Long> safeIds = memberIds.isEmpty() ? Set.of(-1L) : Set.copyOf(memberIds);
            Instant start = date.atStartOfDay(KOREA).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(KOREA).toInstant();
            Specification<ChatRoom> specification = (root, criteria, builder) -> {
                var activityDate = builder.<Instant>coalesce()
                        .value(root.get("lastMessageAt")).value(root.get("createdAt"));
                var dateRange = builder.and(builder.greaterThanOrEqualTo(activityDate, start),
                        builder.lessThan(activityDate, end));
                if (normalizedQuery.isBlank()) return dateRange;
                return builder.and(dateRange, builder.or(root.get("requesterMemberId").in(safeIds),
                        root.get("workerMemberId").in(safeIds)));
            };
            rooms = chatRoomRepository.findAll(specification,
                    PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Order.desc("lastMessageAt"),
                            org.springframework.data.domain.Sort.Order.desc("updatedAt")))).getContent();
        } else if (normalizedQuery.isBlank()) {
            rooms = chatRoomRepository.findTop100ByOrderByLastMessageAtDescUpdatedAtDesc();
        } else {
            List<Long> memberIds = memberRepository.findIdsByNicknameOrEmail(normalizedQuery);
            Set<Long> safeIds = memberIds.isEmpty() ? Set.of(-1L) : Set.copyOf(memberIds);
            rooms = chatRoomRepository.findByParticipantIds(safeIds, PageRequest.of(0, 100));
        }
        Map<Long, Member> members = memberMap(rooms.stream()
                .flatMap(room -> java.util.stream.Stream.of(room.getRequesterMemberId(), room.getWorkerMemberId()))
                .distinct().toList());
        return new AdminChatListView(rooms.stream().map(room -> new AdminChatListView.RoomRow(
                room.getId(), room.getTaskId(), room.getTaskTitle(), memberName(members, room.getRequesterMemberId()),
                memberName(members, room.getWorkerMemberId()), chatMessageRepository.countByRoomId(room.getId()),
                room.getLastMessagePreview(), format(room.getLastMessageAt()), room.isRequesterLeft(), room.isWorkerLeft()
        )).toList());
    }

    @Transactional(readOnly = true)
    public AdminChatDetailView getChatRoom(Long roomId) {
        ChatRoom room = requireRoom(roomId);
        Map<Long, Member> members = memberMap(List.of(room.getRequesterMemberId(), room.getWorkerMemberId()));
        List<ChatMessage> messages = new ArrayList<>(chatMessageRepository
                .findTop100ByRoomIdOrderBySentAtDescIdDesc(roomId));
        Collections.reverse(messages);
        List<AdminChatDetailView.MessageRow> rows = messages.stream().map(message ->
                new AdminChatDetailView.MessageRow(
                        message.getId(), memberName(members, message.getSenderId()), message.getContent(),
                        message.getAttachmentName(), message.getAttachmentSize(), format(message.getSentAt()),
                        format(message.getReadAt()), message.isModerated(), message.getModerationReason(),
                        format(message.getModeratedAt())
                )).toList();
        return new AdminChatDetailView(room.getId(), room.getTaskId(), room.getTaskTitle(),
                memberName(members, room.getRequesterMemberId()), memberName(members, room.getWorkerMemberId()),
                chatMessageRepository.countByRoomId(roomId), rows);
    }

    @Transactional
    public Long blindMessage(Long adminId, Long messageId, String reason) {
        requireAdmin(adminId);
        ChatMessage message = requireMessage(messageId);
        if (message.isModerated()) throw new IllegalStateException("이미 블라인드된 메시지입니다.");
        String normalizedReason = requireReason(reason);
        message.blind(adminId, normalizedReason, Instant.now());
        refreshRoomPreview(message.getRoomId());
        audit(adminId, "CHAT_MESSAGE_BLINDED", "CHAT_MESSAGE", messageId, normalizedReason);
        return message.getRoomId();
    }

    @Transactional
    public Long restoreMessage(Long adminId, Long messageId, String reason) {
        requireAdmin(adminId);
        ChatMessage message = requireMessage(messageId);
        if (!message.isModerated()) throw new IllegalStateException("블라인드되지 않은 메시지입니다.");
        String normalizedReason = requireReason(reason);
        message.restore();
        refreshRoomPreview(message.getRoomId());
        audit(adminId, "CHAT_MESSAGE_RESTORED", "CHAT_MESSAGE", messageId, normalizedReason);
        return message.getRoomId();
    }

    @Transactional(readOnly = true)
    public URI createAdminAttachmentDownloadUrl(Long adminId, Long messageId) {
        requireAdmin(adminId);
        ChatMessage message = requireMessage(messageId);
        if (!StringUtils.hasText(message.getAttachmentObjectPath())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없습니다.");
        }
        return taskStorageService.createSignedDownloadUrl(message.getAttachmentObjectPath());
    }

    @Transactional(readOnly = true)
    public AdminTaskProgressView getTaskProgress(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
        Map<Long, Member> members = memberMap(task.getWorkerId() == null
                ? List.of(task.getRequesterId()) : List.of(task.getRequesterId(), task.getWorkerId()));
        Submission submission = submissionRepository.findByTaskId(taskId).orElse(null);
        Review review = reviewRepository.findByTaskId(taskId).orElse(null);
        Dispute dispute = disputeRepository.findByTaskId(taskId).orElse(null);
        ChatRoom room = chatRoomRepository.findByTaskId(taskId).orElse(null);
        return new AdminTaskProgressView(
                task.getId(), task.getTitle(), task.getStatus().name(), task.getStatus().getLabel(),
                task.getCategory().getLabel(), memberName(members, task.getRequesterId()),
                task.getWorkerId() == null ? "미정" : memberName(members, task.getWorkerId()),
                task.getRequestedMinutes() / 30, format(task.getDeadlineAt()),
                task.getDeadlineAt().isBefore(Instant.now()) && !List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED).contains(task.getStatus()),
                timeline(task), submissionRow(submission), reviewRow(review), disputeRow(dispute, members),
                room == null ? null : room.getId(), room == null ? 0 : chatMessageRepository.countByRoomId(room.getId())
        );
    }

    private List<AdminTaskProgressView.TimelineRow> timeline(Task task) {
        List<AdminTaskProgressView.TimelineRow> rows = new ArrayList<>();
        rows.add(timeline("등록", task.getCreatedAt(), task.getStatus() == TaskStatus.OPEN));
        rows.add(timeline("매칭", task.getMatchedAt(), task.getStatus() == TaskStatus.MATCHED));
        rows.add(timeline("작업 시작", task.getStartedAt(), task.getStatus() == TaskStatus.IN_PROGRESS));
        rows.add(timeline("결과 제출", task.getSubmittedAt(), task.getStatus() == TaskStatus.SUBMITTED));
        rows.add(timeline("완료", task.getCompletedAt(), task.getStatus() == TaskStatus.COMPLETED));
        if (task.getCancelledAt() != null) rows.add(timeline("취소", task.getCancelledAt(), true));
        if (task.getStatus() == TaskStatus.DISPUTED) rows.add(new AdminTaskProgressView.TimelineRow("분쟁 접수", "처리 중", true, true));
        return rows;
    }

    private AdminTaskProgressView.TimelineRow timeline(String step, Instant time, boolean current) {
        return new AdminTaskProgressView.TimelineRow(step, format(time), time != null, current);
    }

    private AdminTaskProgressView.SubmissionRow submissionRow(Submission value) {
        return value == null ? null : new AdminTaskProgressView.SubmissionRow(
                value.getResultDescription(), value.getActualMinutes() / 30, value.getRequesterNote(),
                StringUtils.hasText(value.getResultFileUrl()), format(value.getCreatedAt()), format(value.getUpdatedAt()));
    }

    private AdminTaskProgressView.ReviewRow reviewRow(Review value) {
        return value == null ? null : new AdminTaskProgressView.ReviewRow(
                value.getRating(), value.getContent(), value.getDeadlineMet(), format(value.getCreatedAt()));
    }

    private AdminTaskProgressView.DisputeRow disputeRow(Dispute value, Map<Long, Member> members) {
        return value == null ? null : new AdminTaskProgressView.DisputeRow(
                value.getStatus(), memberName(members, value.getOpenedByMemberId()), value.getDescription(),
                value.getResolutionNote(), format(value.getCreatedAt()), format(value.getResolvedAt()));
    }

    private void refreshRoomPreview(Long roomId) {
        ChatRoom room = requireRoom(roomId);
        ChatMessage latest = chatMessageRepository.findTopByRoomIdOrderBySentAtDescIdDesc(roomId).orElse(null);
        if (latest == null) {
            room.refreshLastMessage(null, null);
            return;
        }
        String preview = latest.isModerated() ? BLINDED_PREVIEW : previewOf(latest);
        room.refreshLastMessage(preview, latest.getSentAt());
    }

    private String previewOf(ChatMessage message) {
        String preview = StringUtils.hasText(message.getContent()) ? message.getContent()
                : "첨부 파일: " + message.getAttachmentName();
        return preview.length() > 500 ? preview.substring(0, 500) : preview;
    }

    private Member requireAdmin(Long adminId) {
        Member admin = memberRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."));
        if (admin.getRole() != MemberRole.ADMIN || admin.getStatus() != MemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return admin;
    }

    private ChatRoom requireRoom(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
    }

    private ChatMessage requireMessage(Long messageId) {
        return chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("관리 사유를 입력해주세요.");
        String normalized = reason.trim();
        if (normalized.length() > 450) throw new IllegalArgumentException("관리 사유는 450자 이하로 입력해주세요.");
        return normalized;
    }

    private Map<Long, Member> memberMap(List<Long> memberIds) {
        Map<Long, Member> result = new HashMap<>();
        memberRepository.findAllById(memberIds).forEach(member -> result.put(member.getId(), member));
        return result;
    }

    private String memberName(Map<Long, Member> members, Long memberId) {
        Member member = members.get(memberId);
        return member == null ? "회원 #" + memberId : member.getNickname();
    }

    private void audit(Long adminId, String action, String targetType, Long targetId, String details) {
        auditLogRepository.save(AdminAuditLog.create(adminId, action, targetType, targetId,
                details.length() > 1_000 ? details.substring(0, 1_000) : details));
    }

    private String format(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(KOREA));
    }
}
