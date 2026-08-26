package org.example._nd_project.admin;

import java.util.List;

public record AdminChatDetailView(Long roomId, Long taskId, String taskTitle,
                                  String requesterName, String workerName, long messageCount,
                                  List<MessageRow> messages) {
    public record MessageRow(Long id, String senderName, String content, String attachmentName,
                             Long attachmentSize, String sentAtLabel, String readAtLabel,
                             boolean moderated, String moderationReason, String moderatedAtLabel) {
        public boolean hasAttachment() {
            return attachmentName != null && !attachmentName.isBlank();
        }
    }
}
