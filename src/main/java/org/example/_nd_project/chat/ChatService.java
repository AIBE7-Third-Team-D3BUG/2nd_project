package org.example._nd_project.chat;

import org.example._nd_project.member.Member;
import org.example._nd_project.member.MemberRepository;
import org.example._nd_project.security.MemberPrincipal;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Profile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@Profile("db")
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;

    public ChatService(ChatRoomRepository chatRoomRepository,
                       ChatMessageRepository chatMessageRepository,
                       MemberRepository memberRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void ensureDemoRoomFor(MemberPrincipal principal) {
        boolean hasRoom = chatRoomRepository.existsByRequesterMemberIdOrWorkerMemberId(principal.memberId(), principal.memberId());
        if (hasRoom) {
            seedDemoHistoryIfNeeded(principal);
            return;
        }

        Member other = memberRepository.findAll().stream()
                .filter(member -> !member.getId().equals(principal.memberId()))
                .findFirst()
                .orElse(null);
        if (other == null) {
            return;
        }

        ChatRoom room = new ChatRoom(
                1L,
                principal.memberId(),
                other.getId(),
                "AWS 배포 후 502 오류 해결",
                "IN_PROGRESS"
        );
        room.refreshLastMessage("대화를 시작했습니다.", Instant.now());
        ChatRoom savedRoom = chatRoomRepository.save(room);
        seedDemoMessages(savedRoom, principal);
    }

    @Transactional
    public void seedDemoHistoryIfNeeded(MemberPrincipal principal) {
        ChatRoom room = chatRoomRepository.findByRequesterMemberIdOrWorkerMemberIdOrderByUpdatedAtDesc(principal.memberId(), principal.memberId())
                .stream()
                .findFirst()
                .orElse(null);
        if (room == null) {
            return;
        }
        if (!chatMessageRepository.findTop30ByRoomIdOrderBySentAtAscIdAsc(room.getId()).isEmpty()) {
            return;
        }
        seedDemoMessages(room, principal);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomView> getRoomsFor(MemberPrincipal principal) {
        return chatRoomRepository.findByRequesterMemberIdOrWorkerMemberIdOrderByUpdatedAtDesc(principal.memberId(), principal.memberId())
                .stream()
                .map(room -> new ChatRoomView(
                        room.getId(),
                        room.getTaskId(),
                        room.getTaskTitle(),
                        room.getTaskStatus(),
                        participantName(room, principal.memberId()),
                        summarizePreview(room.getLastMessagePreview()),
                        room.getLastMessageAt(),
                        chatMessageRepository.findTop30ByRoomIdOrderBySentAtAscIdAsc(room.getId()).stream()
                                .map(message -> toView(message, principal.memberId()))
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public ChatMessageView saveMessage(Long roomId, Long senderId, String content) {
        return saveMessage(roomId, senderId, content, null);
    }

    @Transactional
    public ChatMessageView saveMessage(Long roomId, Long senderId, String content, MultipartFile attachment) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        AttachmentInfo attachmentInfo = storeAttachment(attachment);
        String normalizedContent = normalizeContent(content, attachmentInfo);
        ChatMessage message = chatMessageRepository.save(new ChatMessage(
                roomId,
                senderId,
                normalizedContent,
                attachmentInfo.name(),
                attachmentInfo.path(),
                attachmentInfo.size()
        ));
        room.refreshLastMessage(buildLastMessagePreview(normalizedContent, attachmentInfo), message.getSentAt());
        chatRoomRepository.save(room);
        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("보낸 사람을 찾을 수 없습니다."));
        return toView(message, senderId, sender.getNickname(), true);
    }

    @Transactional(readOnly = true)
    public ChatRoomView getRoomFor(MemberPrincipal principal, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        return new ChatRoomView(
                room.getId(),
                room.getTaskId(),
                room.getTaskTitle(),
                room.getTaskStatus(),
                participantName(room, principal.memberId()),
                summarizePreview(room.getLastMessagePreview()),
                room.getLastMessageAt(),
                chatMessageRepository.findTop30ByRoomIdOrderBySentAtAscIdAsc(room.getId()).stream()
                        .map(message -> toView(message, principal.memberId()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AttachmentDownload getAttachmentDownload(Long messageId, Long memberId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        ChatRoom room = chatRoomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        if (!isRoomMember(room, memberId)) {
            throw new IllegalArgumentException("첨부 파일에 접근할 수 없습니다.");
        }
        if (message.getAttachmentPath() == null || message.getAttachmentName() == null) {
            throw new IllegalArgumentException("첨부 파일이 없습니다.");
        }

        Path path = Path.of(message.getAttachmentPath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("첨부 파일을 찾을 수 없습니다.");
        }
        return new AttachmentDownload(
                new FileSystemResource(path),
                message.getAttachmentName(),
                message.getAttachmentSize(),
                detectContentType(path, message.getAttachmentName()),
                isPreviewableImage(message.getAttachmentName(), path)
        );
    }

    private ChatMessageView toView(ChatMessage message, Long currentMemberId) {
        Member sender = memberRepository.findById(message.getSenderId())
                .orElseThrow(() -> new IllegalArgumentException("메시지 작성자를 찾을 수 없습니다."));
        return new ChatMessageView(message.getId(), sender.getId(), sender.getNickname(), message.getContent(), message.getAttachmentName(), message.getAttachmentPath(), message.getAttachmentSize(), message.getSentAt(), sender.getId().equals(currentMemberId));
    }

    private ChatMessageView toView(ChatMessage message, Long senderId, String nickname, boolean mine) {
        return new ChatMessageView(message.getId(), senderId, nickname, message.getContent(), message.getAttachmentName(), message.getAttachmentPath(), message.getAttachmentSize(), message.getSentAt(), mine);
    }

    private String participantName(ChatRoom room, Long currentMemberId) {
        Long otherId = room.getRequesterMemberId().equals(currentMemberId) ? room.getWorkerMemberId() : room.getRequesterMemberId();
        return memberRepository.findById(otherId).map(Member::getNickname).orElse("알 수 없음");
    }

    private boolean isRoomMember(ChatRoom room, Long memberId) {
        return room.getRequesterMemberId().equals(memberId) || room.getWorkerMemberId().equals(memberId);
    }

    private void seedDemoMessages(ChatRoom room, MemberPrincipal principal) {
        Member other = memberRepository.findById(room.getRequesterMemberId().equals(principal.memberId()) ? room.getWorkerMemberId() : room.getRequesterMemberId())
                .orElseThrow(() -> new IllegalArgumentException("대화 상대를 찾을 수 없습니다."));
        Instant base = Instant.now().minusSeconds(1800);
        chatMessageRepository.save(new ChatMessage(room.getId(), other.getId(), "배포 직후에는 정상인데, 5분 뒤부터 502가 계속 떠요."));
        chatMessageRepository.save(new ChatMessage(room.getId(), principal.memberId(), "네, 로그를 확인해보고 원인을 먼저 좁혀보겠습니다."));
        chatMessageRepository.save(new ChatMessage(room.getId(), other.getId(), "nginx 로그도 같이 볼 수 있을까요?"));
        chatMessageRepository.save(new ChatMessage(room.getId(), principal.memberId(), "확인했습니다. upstream 연결 문제 가능성이 높습니다."));
        room.refreshLastMessage("upstream 연결 문제 가능성이 높습니다.", base.plusSeconds(1800));
        chatRoomRepository.save(room);
    }

    private String summarizePreview(String preview) {
        if (preview == null || preview.isBlank()) {
            return null;
        }
        String normalized = preview.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 36 ? normalized : normalized.substring(0, 35) + "…";
    }

    private AttachmentInfo storeAttachment(MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return new AttachmentInfo(null, null, null);
        }
        try {
            Path uploadDir = Path.of(System.getProperty("user.dir"), "uploads", "chat");
            Files.createDirectories(uploadDir);
            String originalName = attachment.getOriginalFilename() == null ? "attachment" : attachment.getOriginalFilename();
            String safeName = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = uploadDir.resolve(safeName);
            Files.copy(attachment.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new AttachmentInfo(originalName, target.toString(), attachment.getSize());
        } catch (IOException exception) {
            throw new IllegalStateException("파일을 저장하지 못했습니다.", exception);
        }
    }

    private record AttachmentInfo(String name, String path, Long size) {
    }

    private String normalizeContent(String content, AttachmentInfo attachmentInfo) {
        String trimmed = content == null ? "" : content.trim();
        if (!trimmed.isBlank()) {
            return trimmed;
        }
        if (attachmentInfo.name() != null) {
            return "첨부파일: " + attachmentInfo.name();
        }
        return "메시지";
    }

    private String buildLastMessagePreview(String content, AttachmentInfo attachmentInfo) {
        if (attachmentInfo.name() == null) {
            return content;
        }
        if (content == null || content.isBlank() || content.equals("메시지")) {
            return "첨부파일: " + attachmentInfo.name();
        }
        return content + " · 첨부파일: " + attachmentInfo.name();
    }

    public String formatFileSize(Long size) {
        if (size == null) {
            return "";
        }
        double value = size.doubleValue();
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        if (unitIndex == 0) {
            return String.format(Locale.KOREA, "%d %s", size, units[unitIndex]);
        }
        return String.format(Locale.KOREA, "%.1f %s", value, units[unitIndex]);
    }

    private boolean isPreviewableImage(String filename, Path path) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".webp")
                || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".svg")
                || (Files.isRegularFile(path) && probeStartsWithImage(path));
    }

    private boolean probeStartsWithImage(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null && contentType.startsWith("image/");
        } catch (IOException exception) {
            return false;
        }
    }

    private String detectContentType(Path path, String filename) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null) {
                return contentType;
            }
        } catch (IOException ignored) {
        }
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".bmp")) return "image/bmp";
        if (lowerName.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    public record AttachmentDownload(Resource resource, String filename, Long size, String contentType, boolean previewableImage) {
    }
}
