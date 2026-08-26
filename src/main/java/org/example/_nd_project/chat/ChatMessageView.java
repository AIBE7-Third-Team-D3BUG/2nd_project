package org.example._nd_project.chat;
public record ChatMessageView(Long id, Long senderId, String senderNickname, String content,
                              String attachmentName, Long attachmentSize, boolean previewableImage,
                              String sentAtLabel, boolean mine, boolean read) {
    public boolean hasAttachment() { return attachmentName != null && !attachmentName.isBlank(); }
}
