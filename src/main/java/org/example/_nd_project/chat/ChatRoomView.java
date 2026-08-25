package org.example._nd_project.chat;
import java.util.List;
public record ChatRoomView(Long id, Long taskId, String taskTitle, Long otherMemberId, String otherMemberNickname,
                           String lastMessagePreview, String lastMessageAtLabel, long unreadCount,
                           List<ChatMessageView> messages) {}
