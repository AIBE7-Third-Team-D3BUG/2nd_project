package org.example._nd_project.admin;

import java.util.List;

public record AdminChatListView(List<RoomRow> rooms) {
    public record RoomRow(Long id, Long taskId, String taskTitle, String requesterName,
                          String workerName, long messageCount, String lastMessagePreview,
                          String lastMessageAtLabel, boolean requesterLeft, boolean workerLeft) {}
}

