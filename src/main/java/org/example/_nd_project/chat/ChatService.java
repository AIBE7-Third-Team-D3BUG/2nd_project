package org.example._nd_project.chat;

import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.task.Task;
import org.example._nd_project.task.TaskStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.example._nd_project.task.TaskRepository;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final long MAX_ATTACHMENT_SIZE = 6L * 1024 * 1024;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final TaskStorageService taskStorageService;
    private final TaskRepository taskRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatMessageRepository chatMessageRepository,
                       MemberRepository memberRepository,
                       TaskStorageService taskStorageService,
                       TaskRepository taskRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memberRepository = memberRepository;
        this.taskStorageService = taskStorageService;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public ChatRoom ensureRoomForTask(Task task) {
        if (task == null || task.getId() == null || task.getRequesterId() == null || task.getWorkerId() == null) {
            throw new IllegalArgumentException("매칭이 완료된 업무만 채팅방을 만들 수 있습니다.");
        }
        return chatRoomRepository.findByTaskId(task.getId()).orElseGet(() ->
                chatRoomRepository.saveAndFlush(ChatRoom.create(
                        task.getId(), task.getRequesterId(), task.getWorkerId(), task.getTitle())));
    }

    @Transactional
    public void deleteRoomForTask(Long taskId) {
        chatRoomRepository.findByTaskId(taskId).ifPresent(room -> {
            chatMessageRepository.findByRoomId(room.getId()).stream()
                    .map(ChatMessage::getAttachmentObjectPath)
                    .filter(StringUtils::hasText)
                    .forEach(taskStorageService::deleteQuietly);
            chatRoomRepository.delete(room);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatRoomView> getRooms(Long memberId) {
        return chatRoomRepository
                .findByRequesterMemberIdOrWorkerMemberIdOrderByLastMessageAtDescUpdatedAtDesc(memberId, memberId)
                .stream()
                .filter(room -> !room.hasLeft(memberId))
                .map(room -> toRoomView(room, memberId, List.of()))
                .toList();
    }

    @Transactional
    public ChatRoomView openRoom(Long roomId, Long memberId) {
        ChatRoom room = requireRoomMember(roomId, memberId);
        room.reenter(memberId);
        chatMessageRepository.markRead(roomId, memberId, Instant.now());
        List<ChatMessage> recentMessages = new ArrayList<>(chatMessageRepository
                .findTop100ByRoomIdOrderBySentAtDescIdDesc(roomId));
        Collections.reverse(recentMessages);
        String requesterName = nicknameOf(room.getRequesterMemberId());
        String workerName = nicknameOf(room.getWorkerMemberId());
        List<ChatMessageView> messages = recentMessages.stream()
                .map(message -> toMessageView(message, memberId, room, requesterName, workerName))
                .toList();
        return toRoomView(room, memberId, messages);
    }

    @Transactional
    public void leaveRoom(Long roomId, Long memberId) {
        ChatRoom room = requireRoomMember(roomId, memberId);
        if (room.hasLeft(memberId)) {
            return;
        }
        String memberNickname = nicknameOf(memberId);
        ChatMessage leaveNotice = ChatMessage.create(
                roomId, memberId, memberNickname + "님이 채팅방을 나갔습니다.",
                null, null, null, null);
        chatMessageRepository.save(leaveNotice);
        room.refreshLastMessage("채팅방을 나갔습니다.", leaveNotice.getSentAt());
        room.leave(memberId);
    }

    @Transactional
    public Long findRoomIdForTask(Long taskId, Long memberId) {
        ChatRoom room = chatRoomRepository.findByTaskId(taskId).orElse(null);
        if (room == null) {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
            if (task.getWorkerId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "작업자가 매칭된 이후에 채팅이 가능합니다.");
            }
            if (!task.isParticipant(memberId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "업무 참여자만 채팅방에 접근할 수 있습니다.");
            }
            room = ensureRoomForTask(task);
        } else {
            requireMembership(room, memberId);
        }
        return room.getId();
    }

    @Transactional
    public void sendMessage(Long roomId, Long senderId, String content, MultipartFile attachment) {
        ChatRoom room = requireRoomMember(roomId, senderId);
        String normalizedContent = content == null ? "" : content.trim();
        boolean hasAttachment = attachment != null && !attachment.isEmpty();
        if (hasAttachment && attachment.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("첨부 파일은 6MB 이하만 업로드할 수 있습니다.");
        }
        if (!StringUtils.hasText(normalizedContent) && !hasAttachment) {
            throw new IllegalArgumentException("메시지 또는 첨부 파일을 입력해주세요.");
        }
        if (normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("메시지는 2,000자 이하로 입력해주세요.");
        }

        TaskStorageService.StoredObject stored = null;
        try {
            if (hasAttachment) {
                stored = taskStorageService.uploadChatAttachment(roomId, attachment);
            }
            String originalName = hasAttachment ? safeFilename(attachment.getOriginalFilename()) : null;
            ChatMessage message = ChatMessage.create(
                    roomId,
                    senderId,
                    normalizedContent,
                    originalName,
                    stored == null ? null : stored.objectPath(),
                    stored == null ? null : stored.contentType(),
                    hasAttachment ? attachment.getSize() : null
            );
            chatMessageRepository.save(message);
            room.refreshLastMessage(previewOf(normalizedContent, originalName), message.getSentAt());
        } catch (RuntimeException exception) {
            if (stored != null) {
                taskStorageService.deleteQuietly(stored.objectPath());
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(Long messageId, Long memberId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없습니다."));
        requireRoomMember(message.getRoomId(), memberId);
        if (!StringUtils.hasText(message.getAttachmentObjectPath())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없습니다.");
        }
        return taskStorageService.createSignedDownloadUrl(message.getAttachmentObjectPath());
    }

    private ChatRoom requireRoomMember(Long roomId, Long memberId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."));
        requireMembership(room, memberId);
        return room;
    }

    private void requireMembership(ChatRoom room, Long memberId) {
        if (memberId == null || !room.hasMember(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "채팅방 참여자만 이용할 수 있습니다.");
        }
    }

    private ChatRoomView toRoomView(ChatRoom room, Long memberId, List<ChatMessageView> messages) {
        Long otherMemberId = room.otherMemberId(memberId);
        String otherNickname = memberRepository.findById(otherMemberId)
                .map(Member::getNickname)
                .orElse("사용자");
        long unreadCount = chatMessageRepository
                .countByRoomIdAndSenderIdNotAndReadAtIsNull(room.getId(), memberId);
        return new ChatRoomView(
                room.getId(), room.getTaskId(), room.getTaskTitle(), otherMemberId, otherNickname,
                StringUtils.hasText(room.getLastMessagePreview()) ? room.getLastMessagePreview() : "대화를 시작해보세요.",
                formatTime(room.getLastMessageAt()), unreadCount, messages
        );
    }

    private ChatMessageView toMessageView(ChatMessage message, Long memberId, ChatRoom room,
                                          String requesterName, String workerName) {
        boolean mine = message.getSenderId().equals(memberId);
        String senderNickname = message.getSenderId().equals(room.getRequesterMemberId())
                ? requesterName : workerName;
        boolean previewableImage = StringUtils.hasText(message.getAttachmentContentType())
                && message.getAttachmentContentType().startsWith("image/");
        return new ChatMessageView(
                message.getId(), message.getSenderId(), senderNickname, message.getContent(),
                message.getAttachmentName(), message.getAttachmentSize(), previewableImage,
                formatTime(message.getSentAt()), mine, message.getReadAt() != null
        );
    }

    private String previewOf(String content, String attachmentName) {
        String preview = StringUtils.hasText(content) ? content : "첨부 파일: " + attachmentName;
        return preview.length() > 500 ? preview.substring(0, 500) : preview;
    }

    private String safeFilename(String filename) {
        String normalized = StringUtils.hasText(filename) ? filename.replace('\\', '/') : "첨부 파일";
        String value = normalized.substring(normalized.lastIndexOf('/') + 1);
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String formatTime(Instant instant) {
        return instant == null ? "" : TIME_FORMAT.format(instant.atZone(KOREA));
    }

    private String nicknameOf(Long memberId) {
        return memberRepository.findById(memberId).map(Member::getNickname).orElse("사용자");
    }
}
